package com.openclassrooms.tourguide.tracker;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Background tracker that periodically updates user locations
 */
public class Tracker extends Thread {

	private final Logger logger = LoggerFactory.getLogger(Tracker.class);

	/** Interval between tracking cycles (default: 5 minutes). */
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);

	/** Executor managing the tracker thread. */
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	/** Reference to the main service that handles user tracking and rewards. */
	private final TourGuideService tourGuideService;

	/** Flag used to signal the thread to stop gracefully. */
	private boolean stop = false;

	/**
	 * Constructs a new {@code Tracker} and immediately submits it to its executor.
	 *
	 * @param tourGuideService the {@link TourGuideService} used for tracking user locations
	 */
	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;
		executorService.submit(this);
	}

	/**
	 * Stops the tracking process and shuts down the executor service.
	 * <p>
	 * Once called, the tracker will no longer run periodic location updates.
	 * </p>
	 */
	public void stopTracking() {
		stop = true;
		executorService.shutdownNow();
	}

	/**
	 * Main execution loop of the Tracker thread.
	 *
	 * <p>
	 * Continuously retrieves all users from {@link TourGuideService},
	 * tracks their latest location, and logs performance metrics.
	 * </p>
	 *
	 * <p>
	 * The thread sleeps for {@code trackingPollingInterval} seconds between cycles.
	 * It can be interrupted externally or stopped using {@link #stopTracking()}.
	 * </p>
	 */
	@Override
	public void run() {
		StopWatch stopWatch = new StopWatch();

		while (true) {
			if (Thread.currentThread().isInterrupted() || stop) {
				logger.debug("Tracker stopping...");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Tracker started. Tracking {} users.", users.size());

			stopWatch.start();

			// Update each user's location by calling TourGuideService
			users.forEach(tourGuideService::trackUserLocation);

			stopWatch.stop();
			logger.debug("Tracker elapsed time: {} seconds.",
					TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

			stopWatch.reset();

			try {
				logger.debug("Tracker sleeping for {} seconds...", trackingPollingInterval);
				TimeUnit.SECONDS.sleep(trackingPollingInterval);
			} catch (InterruptedException e) {
				logger.debug("Tracker interrupted during sleep. Stopping...");
				break;
			}
		}
	}
}
