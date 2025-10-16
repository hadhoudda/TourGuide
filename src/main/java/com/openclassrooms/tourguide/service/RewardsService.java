package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.openclassrooms.tourguide.service.contracts.IRewardsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService implements IRewardsService {

	private static final Logger logger = LogManager.getLogger(RewardsService.class);
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Proximity settings
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	//private int nbreThread = Runtime.getRuntime().availableProcessors();
	private final ExecutorService executorService = Executors.newFixedThreadPool(100 );

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;

	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		this.proximityBuffer = defaultProximityBuffer;
	}

//	@Override
//	public void calculateRewards(User user) {
//		List<VisitedLocation> visitedLocations = user.getVisitedLocations();
//		List<Attraction> attractions = gpsUtil.getAttractions();
//
//		Set<String> alreadyRewarded = user.getUserRewards().stream()
//				.map(r -> r.getAttraction().attractionName)
//				.collect(Collectors.toSet());
//
//		for (VisitedLocation visitedLocation : visitedLocations) {
//			for (Attraction attraction : attractions) {
//				if (!alreadyRewarded.contains(attraction.attractionName)
//						&& nearAttraction(visitedLocation, attraction)) {
//
//					int rewardPoints = getRewardPoints(attraction, user);
//
//					synchronized (user) {
//						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
//					}
//
//					alreadyRewarded.add(attraction.attractionName);
//				}
//			}
//		}
//	}
@Override
public void calculateRewards(User user) {
	logger.debug("Calculating rewards for user: {}", user.getUserName());

	// Copie défensive des données de l'utilisateur
	List<VisitedLocation> visitedLocations = new ArrayList<>(user.getVisitedLocations());
	List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());

	Set<String> rewardedAttractions = user.getUserRewards().stream()
			.map(reward -> reward.getAttraction().attractionName)
			.collect(Collectors.toSet());

	for (VisitedLocation visitedLocation : visitedLocations) {
		for (Attraction attraction : attractions) {
			String attractionName = attraction.attractionName;

			if (!rewardedAttractions.contains(attractionName)
					&& nearAttraction(visitedLocation, attraction)) {

				int rewardPoints = getRewardPoints(attraction, user);

				// Accès synchronisé à l'utilisateur car la liste des rewards est modifiée
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


	@Override
	public void calculateAllUsersRewardsAsync(List<User> users) {
		logger.info("Launching reward calculation for {} users", users.size());

		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() -> {
					try {
						calculateRewards(user);
					} catch (Exception e) {
						logger.error("Error calculating rewards for user: {}", user.getUserName(), e);
					}
				}, executorService))
				.collect(Collectors.toList());

		// Attendre la fin de tous les traitements
		CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		try {
			all.get(); // bloquant jusqu'à ce que tout soit terminé
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error while waiting for reward calculations to complete", e);
			Thread.currentThread().interrupt();
		}

		logger.info("Reward calculation completed for all users");
	}

	@Override
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

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

//	/**
//	 * Optional method to shut down the executor if needed during application shutdown
//	 */
//	public void shutdown() {
//		logger.info("Shutting down RewardsService executor...");
//		executorService.shutdown();
//		try {
//			if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
//				logger.warn("Executor did not terminate in time, forcing shutdown...");
//				executorService.shutdownNow();
//			}
//		} catch (InterruptedException e) {
//			executorService.shutdownNow();
//			Thread.currentThread().interrupt();
//		}
//	}
}
