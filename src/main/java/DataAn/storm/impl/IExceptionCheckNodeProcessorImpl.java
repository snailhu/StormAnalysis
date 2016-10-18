package DataAn.storm.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicLong;

import org.bson.Document;

import DataAn.common.utils.DateUtil;
import DataAn.dto.CaseSpecialDto;
import DataAn.dto.ParamExceptionDto;
import DataAn.mongo.client.MongodbUtil;
import DataAn.mongo.init.InitMongo;
import DataAn.storm.BatchContext;
import DataAn.storm.IDeviceRecord;
import DataAn.storm.exceptioncheck.ExceptionCasePointConfig;
import DataAn.storm.exceptioncheck.ExceptionConfigModel;
import DataAn.storm.exceptioncheck.impl.IPropertyConfigStoreImpl;
import DataAn.storm.interfece.IExceptionCheckNodeProcessor;

/**
 * 根据配置信息 {@link ExceptionConfigModel} 计算异常和特殊作业 {@link #process(IDeviceRecord)}
 * ,然后持久化{@link #persist()}到mogodb里面
 * @author JIAZJ
 */
@SuppressWarnings("serial")
public class IExceptionCheckNodeProcessorImpl implements
		IExceptionCheckNodeProcessor {
	
	private BatchContext batchContext;
	
	Map<String,List<Long>> paramSequence =new HashMap<>();
	
	Map<String,List<ParamExceptionDto>> exceptionDtoMap =new HashMap<>();
	
	Map<String,List<CaseSpecialDto>> casDtoMap =new HashMap<>();
	
	Map<String,List<CaseSpecialDto>> finalCaseDtoMap =new HashMap<>();
	
	Object[] joblistCatch = new Object[]{};
	Object[] exelistCatch = new Object[]{};
	
	String series ="";
	String star ="";
	String deviceName ="";	
	
	@Override
	public Object process(IDeviceRecord deviceRecord) {		
		 series =deviceRecord.getSeries();
		 star =deviceRecord.getStar();
		 deviceName =deviceRecord.getName();	
		String[] paramValues = deviceRecord.getPropertyVals();
		String[] param = deviceRecord.getProperties();
		if(joblistCatch.length==0 && exelistCatch.length==0){
			for(int i=0;i<paramValues.length;i++){
				List<CaseSpecialDto>  csDtoCatch = new ArrayList<CaseSpecialDto>();
				joblistCatch[i] = csDtoCatch;
				List<ParamExceptionDto> paramEs =  new ArrayList<ParamExceptionDto>();
				exelistCatch[i] = paramEs;
			}
		}		
		for(int i=0;i<paramValues.length;i++){
			ExceptionCasePointConfig ecpc =  new IPropertyConfigStoreImpl().getPropertyConfigbyParam(new String[]{series,star,deviceName,deviceRecord.getProperties()[i]});
			long sequence =new AtomicLong(0).incrementAndGet();
			if(ecpc.getJobMax()<Double.parseDouble(paramValues[i])){
				List<CaseSpecialDto>  csDtoCatch = (List<CaseSpecialDto>) joblistCatch[i];
				CaseSpecialDto cDto = new CaseSpecialDto();
				cDto.setDateTime(deviceRecord.getTime());
				cDto.setSeries(deviceRecord.getSeries());
				cDto.setStar(deviceRecord.getStar());
				cDto.setParamName(param[i]);
				cDto.setFrequency(ecpc.getCount());
				cDto.setLimitValue(ecpc.getJobMax());
				cDto.setLimitTime(ecpc.getDelayTime());
				cDto.setSequence(sequence);
				csDtoCatch.add(cDto);
				casDtoMap.put(param[i], csDtoCatch);
			}
			if(ecpc.getExceptionMax()<Double.parseDouble(paramValues[i]) && Double.parseDouble(paramValues[i])<ecpc.getExceptionMin() ){
				List<ParamExceptionDto> paramEs =  (List<ParamExceptionDto>) exelistCatch[i];
				ParamExceptionDto peDto =  new ParamExceptionDto();
				peDto.setParamName(deviceRecord.getProperties()[i]);
				peDto.setSeries(deviceRecord.getSeries());
				peDto.setStar(deviceRecord.getStar());
				peDto.setValue(paramValues[i]);
				peDto.setTime(deviceRecord.getTime());	
				peDto.setSequence(sequence);
				paramEs.add(peDto);	
				exceptionDtoMap.put(param[i], paramEs);
			}
		}		
		return exceptionDtoMap;	
	}

	@Override
	public void persist() throws Exception {		
		for(String param_Name:casDtoMap.keySet()){			
			List<CaseSpecialDto> cDtos = casDtoMap.get(param_Name);
			List<Document> documentList = new ArrayList<Document>();
			List<CaseSpecialDto> finalCaseDtos =  new ArrayList<>();
			List<Long> finalCaseDtosequence =  new ArrayList<>();
			for(int i=0;i<cDtos.size();){				
				int count = cDtos.get(i).getFrequency();
				int limitTime = (int) cDtos.get(i).getLimitTime();
				int endTime = (int)((DateUtil.fromDateStringToLong(cDtos.get(i+count-1).getDateTime()))/60000);
				int startTime =(int)((DateUtil.fromDateStringToLong(cDtos.get(i).getDateTime()))/60000);
				if((endTime-startTime)>=limitTime){
					for(int j =i;j<i+count;j++){
						finalCaseDtos.add(cDtos.get(j));
						finalCaseDtosequence.add(cDtos.get(j).getSequence());
					}
					Document doc = new Document();				
					doc.append("year", DateUtil.format(cDtos.get(i).getDateTime(), "yyyy"));
					doc.append("year_month", DateUtil.format(cDtos.get(i).getDateTime(), "yyyy-MM"));
					doc.append("year_month_day", DateUtil.format(cDtos.get(i).getDateTime(), "yyyy-MM-dd"));
					doc.append("series", cDtos.get(i).getSeries());
					doc.append("star", cDtos.get(i).getStar());
					doc.append("deviceName", cDtos.get(i).getDeviceName());
					doc.append("paramName", cDtos.get(i).getParamName());	
					doc.append("value", cDtos.get(i).getValue());	
					documentList.add(doc);
					i=i+limitTime;
				}else{i++;}				
			}
			MongodbUtil.getInstance().insertMany(InitMongo.getDataBaseNameBySeriesAndStar(series, star), deviceName+"_SpecialCase", documentList);
			finalCaseDtoMap.put(param_Name, finalCaseDtos);
			paramSequence.put(param_Name, finalCaseDtosequence);
		}		
		for(String paramExce:exceptionDtoMap.keySet()){
			List<ParamExceptionDto> paramEs = exceptionDtoMap.get(paramExce);
			List<Document> documentList = new ArrayList<Document>();
			if(finalCaseDtoMap.keySet().contains(paramExce)){
				List<Long> paramSe = paramSequence.get(paramExce);			
				if(paramSe!=null && paramSe.size()>0){
					for(ParamExceptionDto ped:paramEs){
						if(!(paramSe.contains(ped.getSequence()))){
							Document doc = new Document();	
							doc.append("year", DateUtil.format(ped.getTime(), "yyyy"));
							doc.append("year_month", DateUtil.format(ped.getTime(), "yyyy-MM"));
							doc.append("year_month_day", DateUtil.format(ped.getTime(), "yyyy-MM-dd"));
							doc.append("series", ped.getSeries());
							doc.append("star", ped.getStar());
							doc.append("deviceName", ped.getDeviceName());	
							doc.append("paramName", ped.getParamName());	
							doc.append("value", ped.getValue());	
							documentList.add(doc);
						}
					}
				}
			}
			MongodbUtil.getInstance().insertMany(InitMongo.getDataBaseNameBySeriesAndStar(series, star), deviceName+"_Exception", documentList);
		}
					
	}

	@Override
	public void setBatchContext(BatchContext batchContext) {
		this.batchContext=batchContext;
	}

	@Override
	public BatchContext getBatchContext() {
		return batchContext;
	}

}
