package com.openclassrooms.tourguide.user;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

public class UserReward {

	public final VisitedLocation visitedLocation;
	public final Attraction attraction;
	private int rewardPoints;
	public UserReward(VisitedLocation visitedLocation, Attraction attraction, int rewardPoints) {
		this.visitedLocation = visitedLocation;
		this.attraction = attraction;
		this.rewardPoints = rewardPoints;
	}
	
//	public UserReward(VisitedLocation visitedLocation, Attraction attraction) {
//		this.visitedLocation = visitedLocation;
//		this.attraction = attraction;
//	}
	public UserReward(VisitedLocation visitedLocation, Attraction attraction) {
		this(visitedLocation, attraction, 0); // Par défaut 0 points
	}

//	public void setRewardPoints(int rewardPoints) {
//		this.rewardPoints = rewardPoints;
//	}
//
//	public int getRewardPoints() {
//		return rewardPoints;
//	}
	public VisitedLocation getVisitedLocation() {
		return visitedLocation;
	}

	public Attraction getAttraction() {
		return attraction;
	}

	public int getRewardPoints() {
		return rewardPoints;
	}

	public void setRewardPoints(int rewardPoints) {
		this.rewardPoints = rewardPoints;
	}

}
