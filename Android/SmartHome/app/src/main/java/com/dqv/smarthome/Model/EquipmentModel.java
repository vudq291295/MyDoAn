package com.dqv.smarthome.Model;

public class EquipmentModel {
    public int id;
    public String name;
    public int portOutput;
    public int roomId;
    public int status,chanel;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPortOutput() {
        return portOutput;
    }

    public void setPortOutput(int portOutput) {
        this.portOutput = portOutput;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getChanel() {
        return chanel;
    }

    public void setChanel(int chanel) {
        this.chanel = chanel;
    }
}
