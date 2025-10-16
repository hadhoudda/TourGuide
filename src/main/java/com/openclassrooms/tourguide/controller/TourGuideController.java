package com.openclassrooms.tourguide.controller;

import java.util.ArrayList;
import java.util.List;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.service.RewardsService;
import gpsUtil.location.Location;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import rewardCentral.RewardCentral;
import tripPricer.Provider;

/**
 * TourGuideController handles HTTP requests related to user location,
 * nearby attractions, rewards, and trip deals.
 */
@RestController
public class TourGuideController {

    // Logger for monitoring and debugging
    private static final Logger logger = LogManager.getLogger(TourGuideController.class);

    @Autowired
    TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;

    @Autowired
    RewardCentral rewardCentral;

    /**
     * Default endpoint to check if the application is running.
     *
     * @return a greeting string
     */
    @RequestMapping("/")
    public String index() {
        logger.info("Accessed root endpoint '/'");
        return "Greetings from TourGuide!";
    }

    /**
     * Retrieves the current location of the specified user.
     *
     * @param userName the username of the user
     * @return VisitedLocation object containing the user's current location
     */
    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        logger.info("Request to /getLocation for user: {}", userName);
        return tourGuideService.getUserLocation(getUser(userName));
    }

    /**
     * Retrieves nearby attractions for a user, including reward points and distance.
     *
     * @param userName the username of the user
     * @return List of NearbyAttractionDTOs or 404 if none found
     */
    @RequestMapping("/getNearbyAttractions")
    public ResponseEntity<List<NearbyAttractionDTO>> getNearbyAttractions(@RequestParam String userName) {
        logger.info("Request to /getNearbyAttractions for user: {}", userName);

        // Get user and their current location
        User user = getUser(userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
        Location userLocation = visitedLocation.location;

        // Get nearby attractions
        List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);
        List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();

        for (Attraction attraction : attractions) {
            // Calculate distance between user and attraction
            double distance = rewardsService.getDistance(userLocation, attraction);

            // Get reward points from RewardCentral
            int rewardPoints = rewardCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());

            // Create DTO for frontend/API
            NearbyAttractionDTO dto = new NearbyAttractionDTO(
                    attraction.attractionName,
                    attraction.latitude,
                    attraction.longitude,
                    userLocation.latitude,
                    userLocation.longitude,
                    distance,
                    rewardPoints
            );

            nearbyAttractions.add(dto);
        }

        if (!nearbyAttractions.isEmpty()) {
            logger.info("Found {} nearby attractions for user: {}", nearbyAttractions.size(), userName);
            return new ResponseEntity<>(nearbyAttractions, HttpStatus.OK);
        } else {
            logger.warn("No nearby attractions found for user: {}", userName);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Retrieves reward points for the specified user.
     *
     * @param userName the username of the user
     * @return List of UserReward objects
     */
    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        logger.info("Request to /getRewards for user: {}", userName);
        return tourGuideService.getUserRewards(getUser(userName));
    }

    /**
     * Retrieves trip deals available for the specified user.
     *
     * @param userName the username of the user
     * @return List of Provider objects (trip deals)
     */
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        logger.info("Request to /getTripDeals for user: {}", userName);
        return tourGuideService.getTripDeals(getUser(userName));
    }

    /**
     * Internal method to get a User object by username.
     *
     * @param userName the username
     * @return the User object
     */
    private User getUser(String userName) {
        // No log here as it's a helper used by other endpoints
        return tourGuideService.getUser(userName);
    }
}
