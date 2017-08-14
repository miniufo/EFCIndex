//
package operational;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import miniufo.application.advanced.CoordinateTransformation;
import miniufo.application.basic.DynamicMethodsInCC;
import miniufo.application.basic.ThermoDynamicMethodsInCC;
import miniufo.basic.ArrayUtil;
import miniufo.database.AccessBestTrack;
import miniufo.descriptor.CsmDescriptor;
import miniufo.descriptor.DataDescriptor;
import miniufo.diagnosis.CylindricalSpatialModel;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.diagnosis.MDate;
import miniufo.diagnosis.Range;
import miniufo.diagnosis.SphericalSpatialModel;
import miniufo.diagnosis.Variable;
import miniufo.diagnosis.Variable.Dimension;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;
import miniufo.lagrangian.Record;
import miniufo.lagrangian.Typhoon;

//
public final class AllInOne{
	//
	private static final String path="D:/Data/OperationalIndex/";
	
	private static final DateTimeFormatter fmt=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withLocale(Locale.ENGLISH);
	
	
	//
	public static void main(String[] args){
		TyphoonCMA ty=new TyphoonCMA(path+"babj1705.dat");
		
		computeCase(ty.getAsTyphoon("20170807050000"));
	}
	
	static void computeCase(Typhoon tr){
		DiagnosisFactory df=DiagnosisFactory.parseFile(path+"2017080612.ctl");
		DataDescriptor dd=df.getDataDescriptor();
		
		System.out.println(tr);
		
		String rng="lon(70,140);lat(5,50)";
		
		Range r=new Range(rng,dd);
		Variable[] wind=df.getVariables(r,"u","v");
		
		/*** computing horizontal indices ***/
		Variable[] idx1=IndexInSC.c2DHorizontalIndex(dd,rng,tr,0.3f,19,72,"REFC","PEFC","ETA","ULFI");
		
		for(Variable v:ArrayUtil.concatAll(Variable.class,wind,idx1)) v.setUndef(wind[0].getUndef());
		for(Variable v:idx1) v.setName(v.getName()+"srf");
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,path+"index.dat");
		dw.writeData(dd,ArrayUtil.concatAll(Variable.class,wind,idx1));	dw.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,path+"intensity.dat");
		dw.writeData(dd,AccessBestTrack.toIntensityVariables(tr));	dw.closeFile();
		
		
		/*** computing along-track diagnostics ***/
		DiagnosisFactory df2=DiagnosisFactory.parseContent(tr.toCSMString("",72,19,2,0.3f,-650,850));
		CsmDescriptor csd=(CsmDescriptor)df2.getDataDescriptor();
		
		CylindricalSpatialModel csm=new CylindricalSpatialModel(csd);
		DynamicMethodsInCC dm=new DynamicMethodsInCC(csm);
		ThermoDynamicMethodsInCC tdm=new ThermoDynamicMethodsInCC(csm);
		CoordinateTransformation ct=new CoordinateTransformation(new SphericalSpatialModel(dd),csm);
		
		Variable[] vars=df2.getVariables(new Range("",csd),false,"u","v");
		Variable[] shrs=dm.cVerticalWindShear(vars[0],vars[1]);
		Variable   sst =df2.getVariables(new Range("z(1,1)",csd),false,"sst")[0];
		
		Variable shrsum=dm.cRadialAverage(shrs[0],1,9).anomalizeX();	// within 300 km
		Variable shrsvm=dm.cRadialAverage(shrs[1],1,9).anomalizeX();	// within 300 km
		
		Variable vwsm=shrsum.hypotenuse(shrsvm); vwsm.setName("vws");
		Variable sstm=dm.cRadialAverage(sst,1,9).anomalizeX().minusEq(273.15f);	// within 300 km
		
		Variable[] utvr=ct.reprojectToCylindrical(vars[0],vars[1]);
		Variable Va=utvr[1].copy(); Va.anomalizeX();
		Variable PEFC=dm.cPEFC(Va).averageAlong(Dimension.Y, 9,18);	// 300-600 km
		dm.cStormRelativeAziRadVelocity(tr.getZonalVelocity(),tr.getMeridionalVelocity(),utvr[0],utvr[1]);
		
		Variable utm=utvr[0].anomalizeX();	utvr[1].anomalizeX();
		Variable REFC=dm.cREFC(utvr[0],utvr[1]).averageAlong(Dimension.Y, 9,18);	// 300-600 km
		Variable ETA=dm.cMeanAbsoluteVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable zeta=dm.cMeanRelativeVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable fm=dm.cMeanPlanetaryVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable ISB=dm.cMeanInertialStabilityByUT(utm).averageAlong(Dimension.Y, 9,18);
		Variable ULFI=REFC.plus(PEFC).divideEq(ETA).divideEq(86400f); ULFI.setName("ULFI");
		Variable mpi=tdm.cMPIWNP(sstm);System.out.println(sstm.getData()[0][0][0][10]+"\t"+mpi.getData()[0][0][0][10]);
		
		dw=DataIOFactory.getDataWrite(dd,path+"alongTrackDiags.dat");
		dw.writeData(dd,new Variable[]{sstm,vwsm,REFC,PEFC,ETA,zeta,fm,ISB,ULFI,mpi});	dw.closeFile();
	}
	
	static class TyphoonCMA{
		int num     =0;
		
		String ID   =null;
		String name =null;
		
		Map<String,List<RecordCMA>> recs=null;
		
		public TyphoonCMA(String fname){
			try(Stream<String> tmp=Files.lines(Paths.get(fname))){
				List<String> lines=tmp.collect(Collectors.toList());
				
				String[] tokens=lines.get(0).trim().split("\\s+");
				
				ID  =tokens[0];
				name=tokens[1];
				num =Integer.parseInt(tokens[2]);
				
				recs=lines.stream().skip(1).map(oneline->new RecordCMA(oneline))
					.collect(Collectors.groupingBy(rec->rec.predictTime));
				
			}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		}
		
		
		/**
		 * get the predict records initialized at the given time (UTC, yyyyMMddHHmmss)
		 */
		public List<RecordCMA> getRecordsPredictAt(String time){ return recs.get(time);}
		
		public Typhoon getAsTyphoon(String time){
			Typhoon ty=new Typhoon(ID,name,
				getRecordsPredictAt(time).stream().
				map(r->r.toRecord()).collect(Collectors.toList())
			);
			
			ty.cVelocityByPosition();
			
			return ty;
		}
	}
	
	static class RecordCMA{
		float lon=0;
		float lat=0;
		float prs=0;
		float wnd=0;
		
		String forecastHour=null;
		String predictTime =null;
		
		public RecordCMA(String oneline){
			String[] tokens=oneline.trim().split("\\s+");
			
			predictTime=new MDate(Long.parseLong(tokens[0]+tokens[1]+tokens[2]+tokens[3]+"0000"))
				.toFormattedDate(fmt);
			
			forecastHour=tokens[4];
			
			lon=Float.parseFloat(tokens[5]);
			lat=Float.parseFloat(tokens[6]);
			prs=Float.parseFloat(tokens[7]);
			wnd=Float.parseFloat(tokens[8]);
		}
		
		public Record toRecord(){
			Record r=new Record(Long.valueOf(predictTime),lon,lat,4);
			
			r.setData(0,0  );
			r.setData(1,0  );
			r.setData(2,wnd);
			r.setData(3,prs);
			
			return r;
		}
		
		public String toString(){
			return predictTime+" "+forecastHour+String.format(" %6.2f %6.2f %6.2f %6.2f",lon,lat,prs,wnd);
		}
	}
}
