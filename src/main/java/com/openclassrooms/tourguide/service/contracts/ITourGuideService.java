package com.openclassrooms.tourguide.service.contracts;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

import java.util.List;

public interface ITourGuideService {

    List<UserReward> getUserRewards(User user);
    VisitedLocation getUserLocation(User user);
    User getUser(String userName);
    List<User> getAllUsers();
    void addUser(User user);
    List<Provider> getTripDeals(User user);
    void calculateAllTrackUserLocationAsync(List<User> users) throws InterruptedException;
    VisitedLocation trackUserLocation(User user);
    List<Attraction> getNearByAttractions(VisitedLocation visitedLocation);
}