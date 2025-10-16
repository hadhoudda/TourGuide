package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.service.contracts.IRewardsService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service responsible for calculating and assigning rewards to users
 * based on their visited locations and proximity to known attractions.
 */
@Service
public class RewardsService implements IRewardsService {

	private static final Logger logger = LogManager.getLogger(RewardsService.class);
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	/** Default proximity distance in miles used to check if a user is near an attraction. */
	private final int defaultProximityBuffer = 10;

	/** Current proximity distance in miles, configurable at runtime. */
	private int proximityBuffer = defaultProximityBuffer;

	/** Maximum attraction proximity range in miles. */
	private final int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	/** Thread pool for parallel reward calculations across multiple users. */
	private final ExecutorService executorService = Executors.newFixedThreadPool(100);

	/**
	 * Constructs a {@code RewardsService} with the given GPS and reward providers.
	 *
	 * @param gpsUtil         the GPS utility service used to access attractions
	 * @param rewardCentral   the RewardCentral service used to fetch reward points
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	/**
	 * Sets a custom proximity buffer (in miles) used when checking proximity to attractions.
	 *
	 * @param proximityBuffer the new proximity buffer distance in miles
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Resets the proximity buffer to the default value.
	 */
	public void setDefaultProximityBuffer() {
		this.proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calculates rewards for a single user based on their visited locations
	 * and proximity to attractions.
	 *
	 * <p>If the user has visited a location within the proximity buffer of an attraction
	 * and has not already been rewarded for it, a new {@link UserReward} is added.</p>
	 *
	 * @param user the user whose rewards should be calculated
	 */
	@Override
	public void calculateRewards(User user) {
		List<VisitedLocation> visitedLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());

		// Track already rewarded attractions to avoid duplicate rewards
		Set<String> rewardedAttractions = user.getUserRewards().stream()
				.map(reward -> reward.getAttraction().attractionName)
				.collect(Collectors.toSet());

		for (VisitedLocation visitedLocation : visitedLocations) {
			for (Attraction attraction : attractions) {
				String attractionName = attraction.attractionName;

				if (!rewardedAttractions.contains(attractionName)
						&& nearAttraction(visitedLocation, attraction)) {

					int rewardPoints = getRewardPoints(attraction, user);

					// Synchronize to prevent concurrent modifications of the user's reward list
					synchronized (user) {
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
						logger.debug("Added reward for user: {}, attraction: {}, points: {}",
								user.getUserName(), attractionName, rewardPoints);
					}
					rewardedAttractions.add(attractionName);
				}
			}
		}
	}

	/**
	 * Calculates rewards for all users asynchronously using a fixed thread pool.
	 *
	 * <p>Each user’s rewards are processed in parallel via {@link CompletableFuture}.</p>
	 *
	 * @param users a list of users whose rewards should be calculated
	 */
	@Override
	public void calculateAllUsersRewardsAsync(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() -> {
					try {
						calculateRewards(user);
					} catch (Exception e) {
						logger.error("Error calculating rewards for user: {}", user.getUserName(), e);
					}
				}, executorService))
				.toList();

		// Wait for all reward calculations to complete
		CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		try {
			all.get(); // Blocking call until all async tasks are done
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error while waiting for reward calculations to complete", e);
			Thread.currentThread().interrupt();
		}

		logger.info("Reward calculation completed for all users");
	}

	/**
	 * Checks whether a given location is within the proximity range of an attraction.
	 *
	 * @param attraction the attraction to compare
	 * @param location   the location to check
	 * @return {@code true} if the location is within the attraction proximity range, otherwise {@code false}
	 */
	@Override
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	/**
	 * Determines if a visited location is near a specific attraction.
	 *
	 * @param visitedLocation the user's visited location
	 * @param attraction      the attraction to check
	 * @return {@code true} if the visited location is within the proximity buffer, otherwise {@code false}
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	/**
	 * Retrieves the reward points for a given attraction and user.
	 *
	 * @param attraction the attraction for which to retrieve reward points
	 * @param user       the user receiving the reward
	 * @return the reward points earned
	 */
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calculates the distance between two geographical locations in statute miles.
	 *
	 * <p>This method uses the great-circle distance formula (based on the spherical law of cosines).</p>
	 *
	 * @param loc1 the first location
	 * @param loc2 the second location
	 * @return the distance between the two locations in miles
	 */
	@Override
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}
}
