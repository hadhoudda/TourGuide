package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.contracts.ITourGuideService;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Main service responsible for user tracking, location management,
 * reward calculation, and trip deal retrieval.
 */
@Service
public class TourGuideService implements ITourGuideService {

	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	private final ExecutorService executorService = Executors.newFixedThreadPool(100);
	private final Map<String, User> internalUserMap = new HashMap<>();

	private static final String tripPricerApiKey = "test-server-api-key";
	private boolean testMode = true;

	/**
	 * Constructs a {@code TourGuideService} with required dependencies.
	 *
	 * @param gpsUtil         GPS utility for retrieving user locations and attractions
	 * @param rewardsService  reward service for calculating user rewards
	 */
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing internal users...");
			initializeInternalUsers();
			logger.debug("Finished initializing internal users.");
		}

		tracker = new Tracker(this);
		addShutDownHook();
	}

	/**
	 * Retrieves the list of rewards earned by a given user.
	 *
	 * @param user the user whose rewards are being fetched
	 * @return list of {@link UserReward}
	 */
	@Override
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Returns the last known or current location of a user.
	 *
	 * @param user the user whose location is requested
	 * @return the user's most recent {@link VisitedLocation}
	 */
	@Override
	public VisitedLocation getUserLocation(User user) {
		return (user.getVisitedLocations().size() > 0)
				? user.getLastVisitedLocation()
				: trackUserLocation(user);
	}

	/**
	 * Finds a user by username.
	 *
	 * @param userName the username to search for
	 * @return the matching {@link User} or {@code null} if not found
	 */
	@Override
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * Returns all users currently stored in the system.
	 *
	 * @return list of all {@link User}
	 */
	@Override
	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	/**
	 * Adds a user to the internal map if they don't already exist.
	 *
	 * @param user the {@link User} to add
	 */
	@Override
	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * Retrieves trip deals for a user based on preferences and reward points.
	 *
	 * @param user the user requesting trip deals
	 * @return a list of available {@link Provider} offers
	 */
	@Override
	public List<Provider> getTripDeals(User user) {
		int totalRewardPoints = user.getUserRewards().stream()
				.mapToInt(i -> i.getRewardPoints())
				.sum();

		List<Provider> providers = tripPricer.getPrice(
				tripPricerApiKey,
				user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(),
				user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(),
				totalRewardPoints
		);

		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Asynchronously tracks the location of all users using a thread pool.
	 *
	 * @param users list of users to track
	 * @throws InterruptedException if the execution is interrupted
	 */
	@Override
	public void calculateAllTrackUserLocationAsync(List<User> users) throws InterruptedException {

		List<CompletableFuture<VisitedLocation>> futures = users.stream()
				.map(user -> CompletableFuture.supplyAsync(() -> trackUserLocation(user), executorService))
				.toList();

		// Wait for all tracking tasks to complete
		List<VisitedLocation> visitedLocations = futures.stream()
				.map(CompletableFuture::join)
				.collect(Collectors.toList());

		executorService.shutdown();
	}

	/**
	 * Tracks the current location of a single user and calculates associated rewards.
	 *
	 * @param user the user to track
	 * @return the new {@link VisitedLocation} for the user
	 */
	@Override
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Finds the five closest attractions to the given user location.
	 *
	 * @param visitedLocation the user's current location
	 * @return a list of up to five nearby {@link Attraction}
	 */
	@Override
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		Location userLocation = visitedLocation.location;

		// Sort attractions by proximity to the user's current location
		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(a ->
						rewardsService.getDistance(userLocation, a)))
				.limit(5)
				.collect(Collectors.toList());
	}

	/**
	 * Adds a shutdown hook to stop the tracker gracefully when the application stops.
	 */
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> tracker.stopTracking()));
	}

	// ---------------------------------------------------------------------------
	// Internal test utilities (used only in testMode)
	// ---------------------------------------------------------------------------

	/**
	 * Initializes a predefined number of internal users for testing purposes.
	 */
	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);
			internalUserMap.put(userName, user);
		});
		logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
	}

	/**
	 * Generates a small random location history for internal test users.
	 *
	 * @param user the internal user to populate with locations
	 */
	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> user.addToVisitedLocations(
				new VisitedLocation(user.getUserId(),
						new Location(generateRandomLatitude(), generateRandomLongitude()),
						getRandomTime())));
	}

	/**
	 * Generates a random longitude value between -180 and 180 degrees.
	 */
	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	/**
	 * Generates a random latitude value between -85.05 and 85.05 degrees.
	 */
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	/**
	 * Generates a random timestamp within the last 30 days.
	 */
	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
}
