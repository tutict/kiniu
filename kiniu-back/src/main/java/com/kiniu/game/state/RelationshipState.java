package com.kiniu.game.state;

public class RelationshipState {

    private int trust;
    private int affection;
    private int curiosity;

    public RelationshipState() {
        this(0, 0, 0);
    }

    public RelationshipState(int trust, int affection, int curiosity) {
        this.trust = trust;
        this.affection = affection;
        this.curiosity = curiosity;
    }

    public RelationshipState copy() {
        return new RelationshipState(trust, affection, curiosity);
    }

    public void adjust(int trustDelta, int affectionDelta, int curiosityDelta) {
        this.trust += trustDelta;
        this.affection += affectionDelta;
        this.curiosity += curiosityDelta;
    }

    public int aggregate() {
        return trust + affection + curiosity;
    }

    public int getTrust() {
        return trust;
    }

    public void setTrust(int trust) {
        this.trust = trust;
    }

    public int getAffection() {
        return affection;
    }

    public void setAffection(int affection) {
        this.affection = affection;
    }

    public int getCuriosity() {
        return curiosity;
    }

    public void setCuriosity(int curiosity) {
        this.curiosity = curiosity;
    }
}
