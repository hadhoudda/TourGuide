package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {


	@Test
	public void highVolumeTrackLocation() throws InterruptedException {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = new ArrayList<>();
		allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//		for (User user : allUsers) {
		//	tourGuideService.trackUserLocation(user);
		//}
		tourGuideService.calculateAllTrackUserLocationAsync(allUsers);


		stopWatch.stop();
		tourGuideService.tracker.stopTracking();
		System.out.println("Nombre réel d'utilisateurs : " + allUsers.size());
		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}



	@Test
	public void highVolumeGetRewards() throws InterruptedException {
		InternalTestHelper.setInternalUserNumber(100000);
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		// Simuler la visite d'une attraction pour chaque utilisateur
		Attraction attraction = gpsUtil.getAttractions().get(0);
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		// Mesure du temps
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();


		// Appel de la méthode asynchrone
		rewardsService.calculateAllUsersRewardsAsync(allUsers);

		stopWatch.stop();

		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		tourGuideService.tracker.stopTracking();
		// ferme le thread pool
		//rewardsService.shutdown();
		System.out.println("Nombre réel d'utilisateurs : " + allUsers.size());

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
				+ " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

}
