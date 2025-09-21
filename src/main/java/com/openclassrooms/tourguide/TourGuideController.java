package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.List;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.service.RewardsService;
import gpsUtil.location.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import rewardCentral.RewardCentral;
import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;

    @Autowired
    RewardCentral rewardCentral;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }
    
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral
//    @RequestMapping("/getNearbyAttractions")
//    public List<Attraction> getNearbyAttractions(@RequestParam String userName) {
//    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
//    	return tourGuideService.getNearByAttractions(visitedLocation);
//    }

//    public ResponseEntity<List<Attraction>>  getNearbyAttractions(@RequestParam String userName) {
//        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
//        System.out.println(visitedLocation);
//        List<Attraction> listAttractions = tourGuideService.getNearByAttractions(visitedLocation);
//        System.out.println(listAttractions);
//        if(!listAttractions.isEmpty()){
//            return new ResponseEntity<>(listAttractions, HttpStatus.OK);
//        } else {
//            return new ResponseEntity<>(listAttractions, HttpStatus.NOT_FOUND);
//        }
//
//    }

    @RequestMapping("/getNearbyAttractions")
    public ResponseEntity<List<NearbyAttractionDTO>> getNearbyAttractions(@RequestParam String userName) {
        User user = getUser(userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

        Location userLocation = visitedLocation.location;
        List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);
        List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();

        for (Attraction attraction : attractions) {
            double distance = rewardsService.getDistance(userLocation, attraction);
            //Attraction reward points are collected from RewardsCentral
            int rewardPoints = rewardCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());

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
            return new ResponseEntity<>(nearbyAttractions, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}