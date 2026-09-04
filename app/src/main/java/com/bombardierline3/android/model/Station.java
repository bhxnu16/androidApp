package com.bombardierline3.android.model;

public class Station {
    public String nameEn;
    public String nameHi;
    public String shortNameHi;
    public String shortNameEn;
    public String doorSideEn;
    public String doorSideHi;
    public String interchangeEn;
    public String interchangeHi;
    public int socialIndexForward;
    public int socialIndexBackward;

    public Station() {} // For Jackson

    public Station(String nameEn, String nameHi, String shortNameEn, String shortNameHi, 
                    String doorSideEn, String doorSideHi, String interchangeEn,
                    String interchangeHi, int socialIndexForward, int socialIndexBackward) {
            this.nameEn = nameEn;
            this.nameHi = nameHi;
            this.shortNameEn = shortNameEn;
            this.shortNameHi = shortNameHi;
            this.doorSideEn = doorSideEn;
            this.doorSideHi = doorSideHi;
            this.interchangeEn = interchangeEn;
            this.interchangeHi = interchangeHi;
            this.socialIndexForward = socialIndexForward;
            this.socialIndexBackward = socialIndexBackward;
    }

    public String getNameEn() {
            return this.nameEn;
    }

    public String getNameHi() {
            return this.nameHi;
    }

    public String getShortNameEn() {
            return (this.shortNameEn != null && !this.shortNameEn.isEmpty()) ? this.shortNameEn : this.nameEn;
    }

    public String getShortNameHi() {
            return (this.shortNameHi != null && !this.shortNameHi.isEmpty()) ? this.shortNameHi : this.nameHi;
    }
}
