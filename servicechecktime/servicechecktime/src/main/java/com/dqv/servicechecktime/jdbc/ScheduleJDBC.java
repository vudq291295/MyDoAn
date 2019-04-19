package com.dqv.servicechecktime.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import Model.EquipmentModel;
import Model.SchedueModel;

public class ScheduleJDBC {

	Connection con;
	public ScheduleJDBC(Connection con) {
		this.con = con;
	}
	
	public List<EquipmentModel> getEquipSet(){
		Date in = new Date();
		LocalDateTime ldt = LocalDateTime.ofInstant(in.toInstant(), ZoneId.systemDefault());
		Date out = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());

		List<EquipmentModel> result = new ArrayList<EquipmentModel>();
		List<SchedueModel> resultSchedule = new ArrayList<SchedueModel>();

		String QUERY = "select * from schedule where time_start = '"+ldt.getHour()+":"+ldt.getMinute()
						+"' and  (day is null OR day like '%"+out.getDay()+"%')";
		System.out.println(QUERY);
		try {
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(QUERY);	
			while(rs.next()){
				SchedueModel tempSchedule = new SchedueModel();
				tempSchedule.setScript_id(rs.getInt("script_id"));
				tempSchedule.setEquipment_id(rs.getInt("equipment_id"));
				resultSchedule.add(tempSchedule);
			}
			System.out.println(resultSchedule.size());
			if(resultSchedule.size() > 0) {
				for(int i = 0;i<resultSchedule.size();i++) {
					System.out.println(resultSchedule.get(i).getEquipment_id());
					if(resultSchedule.get(i).getEquipment_id()>0) {
						String QUERY_EQUIPT = "select * from equipment a"
											+" INNER JOIN room b"
											+" ON a.room_id = b.id"
											+" WHERE a.id = "+resultSchedule.get(i).getEquipment_id();
						rs = stmt.executeQuery(QUERY_EQUIPT);	
						while(rs.next()){
							EquipmentModel tempEquipt = new EquipmentModel();
							tempEquipt.setChanel(rs.getInt("chanel"));
							tempEquipt.setPortOutput(rs.getInt("port_output"));
							result.add(tempEquipt);
						}

					}
				}
			}
		}
		catch (Exception e) {
			// TODO: handle exception
		}
		return result;
	}

}
