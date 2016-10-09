package DataAn.storm.impl;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import DataAn.common.utils.DateUtil;
import DataAn.mongo.client.MongodbUtil;
import DataAn.mongo.init.InitMongo;
import DataAn.storm.IDeviceRecord;
import DataAn.storm.interfece.IDeviceRecordPersit;

public class IDeviceRecordPersitImpl implements IDeviceRecordPersit {

	@Override
	public void persist(IDeviceRecord... deviceRecords) throws Exception {
		List<Document> tempList = new ArrayList<Document>();
		String series = "";
		String star = "";
		String deviceName = "";
		for(IDeviceRecord ir :deviceRecords){
			series = ir.getSeries();
			star = ir.getStar();
			deviceName = ir.getName();
			Document doc = new Document();				
			doc.append("status", 1);
			doc.append("year", DateUtil.format(ir.getTime(), "yyyy"));
			doc.append("year_month", DateUtil.format(ir.getTime(), "yyyy-MM"));
			doc.append("year_month_day", DateUtil.format(ir.getTime(), "yyyy-MM-dd"));
			doc.append("series", series);
			doc.append("star", star);
			doc.append("deviceName", deviceName);	
			doc.append("", 1);
			for(int i=0;i<ir.getProperties().length;i++){			
				doc.append(ir.getProperties()[i], ir.getPropertyVals()[i]);
			}
			tempList.add(doc);		
		}		
		MongodbUtil.getInstance().insertMany(InitMongo.getDataBaseNameBySeriesAndStar(series, star), deviceName, tempList);
				
	}

}
