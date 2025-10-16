package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
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

	/**
	 * Tests performance of tracking a high volume of users asynchronously.
	 * The test simulates 100,000 users and ensures that location tracking
	 * completes within 15 minutes.
	 */
	@Test
	public void highVolumeTrackLocation() throws InterruptedException {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Define number of simulated internal users
		InternalTestHelper.setInternalUserNumber(100000);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = new ArrayList<>(tourGuideService.getAllUsers());

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Run asynchronous tracking for all users
		tourGuideService.calculateAllTrackUserLocationAsync(allUsers);

		stopWatch.stop();

		// Stop background tracking thread
		tourGuideService.tracker.stopTracking();

		System.out.println("Actual number of users: " + allUsers.size());
		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		// Assert that execution completes within 15 minutes
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	/**
	 * Tests performance of reward calculation for a large number of users.
	 * The test simulates 100,000 users, each visiting one attraction,
	 * and verifies that reward computation completes within 20 minutes.
	 */
	@Test
	public void highVolumeGetRewards() throws InterruptedException {
		InternalTestHelper.setInternalUserNumber(100000);
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = tourGuideService.getAllUsers();

		// Simulate one attraction visit for each user
		Attraction attraction = gpsUtil.getAttractions().get(0);
		allUsers.forEach(u ->
				u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
		);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Run asynchronous reward calculation
		rewardsService.calculateAllUsersRewardsAsync(allUsers);

		stopWatch.stop();

		// Verify each user received at least one reward
		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		// Stop background tracker thread
		tourGuideService.tracker.stopTracking();

		System.out.println("Actual number of users: " + allUsers.size());
		System.out.println("highVolumeGetRewards: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

		// Assert that execution completes within 20 minutes
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
}