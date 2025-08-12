package com.yourname.streamci.streamci.model;

public class Pipeline {
    private int id;
    private String name;
    private String status;
    private int duration;



    //    constructors
    public Pipeline(){}

    public Pipeline(int id){
        this.id = id;
    }

    public Pipeline(int id, String name){
        this.id = id;
        this.name = name;
    }

    public Pipeline(int id, String name, String status){
        this.id = id;
        this.name = name;
        this.status = status;
    }
    public Pipeline(int id, String name, String status, int duration){
        this.id = id;
        this.name = name;
        this.status = status;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "Pipeline{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", duration=" + duration +
                '}';
    }



    //    getters

    public int getId() {
        return id;
    }

    public int getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }


    //setters


    public void setId(int id) {
        this.id = id;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
