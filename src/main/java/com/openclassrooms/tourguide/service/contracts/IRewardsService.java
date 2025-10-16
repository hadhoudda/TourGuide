package com.openclassrooms.tourguide.service.contracts;

import com.openclassrooms.tourguide.user.User;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;

import java.util.List;

public interface IRewardsService {
    void calculateRewards(User user);
    void calculateAllUsersRewardsAsync(List<User> users) throws InterruptedException;
    boolean isWithinAttractionProximity(Attraction attraction, Location location);
    double getDistance(Location loc1, Location loc2);
}
