//
package operational;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.json.JSONObject;
import org.python.core.PyFunction;
import org.python.core.PyMethod;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;
import miniufo.application.advanced.CoordinateTransformation;
import miniufo.application.basic.DynamicMethodsInCC;
import miniufo.application.basic.IndexInSC;
import miniufo.application.basic.ThermoDynamicMethodsInCC;
import miniufo.basic.ArrayUtil;
import miniufo.basic.InterpolationModel.Type;
import miniufo.database.AccessBestTrack;
import miniufo.database.AccessBestTrack.DataSets;
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
import miniufo.io.IOUtil;
import miniufo.lagrangian.Typhoon;
import miniufo.test.util.OpenGrADS;
import miniufo.util.DataInterpolation;


//
public final class DownloadData{
	//
	static boolean prsData=false;
	
	static final int interpRes=6;	// time interval (in hours) after interpolation
	
	static final Predicate<Typhoon> cond=ty->{
		int year=new MDate(ty.getTime(0)).getYear();
		return year==2016&&(Integer.parseInt(ty.getID())==1612);
	};
	
	static final DataSets    ds=DataSets.JMA;
	static final Reanalysis gds=Reanalysis.ERAInterim;
	static final Basin    basin=Basin.WNP;
	
	static final String path="G:/Data/ULFI/";
	static final String domain=getDomain(basin);
	
	static final List<Typhoon> all=AccessBestTrack.getTyphoons("d:/Data/Typhoons/"+ds+"/"+ds+".txt","",ds);
	static final DateTimeFormatter fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.ENGLISH);
	
	static enum Reanalysis{ ERAInterim,ERA5}
	static enum Basin{ WNP,NAT,SIO}
	
	static String getDomain(Basin basin){
		// get computational domain, smaller than that of downloading data
		switch(basin){
		case WNP: return "lon(90,180);lat(1.5,54)";
		case NAT: return "lon(243,330);lat(1.5,54)";
		case SIO: return "lon(21,111);lat(-1.5,-54)";
		default : throw new IllegalArgumentException("unsupported basin: "+basin);
		}
	}
	
	
	//
	public static void main(String[] args){
		PythonInterpreter interp=new PythonInterpreter();
		interp.execfile("D:/Java/ecmwf-api-client-python/ecmwfapi/api.py");
		interp.exec("parseFunc = json.loads");
		interp.exec("server = ECMWFDataServer()");
		interp.exec("retrieve = server.retrieve");
		
		all.stream().filter(cond).forEach(ty->{
			// downloading NetCDF data
			switch(gds){
			case ERAInterim:
				//JythonDownload(interp,prepareJSONDataInterim(ty));
				//extractDataInterim(ty);					// extract NetCDF data into binary data
				ty.interpolateAlongT(6/interpRes-1);	// interpolate the typhoon data
				computingIndexInterim(ty,6/interpRes);	// computing index
				//generatePlotGS(ty);						// generate GS and plot
				//generateAnimateGS(ty);					// generate animation GS
				break;
				
			case ERA5:
				JythonDownload(interp,prepareJSONDataERA5(ty));
				extractDataERA5(ty);					// extract NetCDF data into binary data
				ty.interpolateAlongT(6/interpRes-1);	// interpolate the typhoon data
				computingIndexERA5(ty);					// computing index
				generatePlotGS(ty);						// generate GS and plot
				generateAnimateGS(ty);					// generate animation GS
				break;

			default:
				throw new IllegalArgumentException("unsupported gridded dataset: "+gds);
			}
		});
		
		interp.close();
	}
	
	static void JythonDownload(PythonInterpreter interp,JSONObject[] jsons){
		PyFunction parser=interp.get("parseFunc"   ,PyFunction.class);
		PyMethod retrieve=interp.get("retrieve",PyMethod.class);
		
		Path p=Paths.get(jsons[0].get("target").toString()).getParent();
		
		if(!Files.exists(p)){
			try{Files.createDirectories(p);}
			catch(IOException e){ e.printStackTrace(); System.exit(0);}
		}
		
		for(JSONObject json:jsons){
			PyObject pyJson=parser.__call__(new PyString(json.toString()));
			retrieve.__call__(pyJson);
		}
	}
	
	static JSONObject[] prepareJSONDataInterim(Typhoon ty){
		int year=new MDate(ty.getTime(0)).getYear();
		
		String id=ty.getID();
		
		MDate tstr=new MDate(ty.getTime(0));
		MDate tend=new MDate(ty.getTime(ty.getTCount()-1));
		
		String date=tstr.toFormattedDate(fmt)+"/to/"+tend.toFormattedDate(fmt);
		String target=path+ds+"/"+year+"/"+id+"/"+id;
		
		JSONObject  reP = new JSONObject();
		reP.put("dataset" , "interim");
		reP.put("date"    , date);
		reP.put("stream"  , "oper");
		reP.put("repres" , "ll");	// sh/ll/gg
		reP.put("levtype" , "pl");	// ml/sfc/pl/pv/pt/dp
		reP.put("levelist" , "850/200");
		reP.put("param"   , "u/v/t");	//
		reP.put("step"    , "0");
		reP.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		reP.put("type"    , "an");	// an/fc
		reP.put("resol"   , "av");
		reP.put("grid"    , "0.75/0.75");
		if(basin==Basin.NAT)
			reP.put("area"    , "78/231/-9/357"); // north/west/south/east
		else if(basin==Basin.WNP)
			reP.put("area"    , "78/81/-9/210"); // north/west/south/east
		else if(basin==Basin.SIO)
			reP.put("area"    , "9/0/-78/111"); // north/west/south/east
		reP.put("format"  , "netcdf");
		reP.put("target"  , target+"Pl.nc");
		
		JSONObject  reS = new JSONObject();
		reS.put("dataset" , "interim");
		reS.put("date"    , date);
		reS.put("stream"  , "oper");
		reS.put("repres" , "ll");	// sh/ll/gg
		reS.put("levtype" , "sfc");	// ml/sfc/pl/pv/pt/dp
		reS.put("param"   , "sst");	//
		reS.put("step"    , "0");
		reS.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		reS.put("type"    , "an");	// an/fc
		reS.put("resol"   , "av");
		reS.put("grid"    , "0.75/0.75");
		if(basin==Basin.NAT)
			reS.put("area"    , "78/231/-9/357"); // north/west/south/east
		else if(basin==Basin.WNP)
			reS.put("area"    , "78/81/-9/210"); // north/west/south/east
		else if(basin==Basin.SIO)
			reS.put("area"    , "9/0/-78/120"); // north/west/south/east
		reS.put("format"  , "netcdf");
		reS.put("target"  , target+"Sfc.nc");
		
		return new JSONObject[]{reP,reS};
	}
	
	static JSONObject[] prepareJSONDataERA5(Typhoon ty){
		int year=new MDate(ty.getTime(0)).getYear();
		
		String id=ty.getID();
		
		MDate tstr=new MDate(ty.getTime(0));
		MDate tend=new MDate(ty.getTime(ty.getTCount()-1));
		
		String date=tstr.toFormattedDate(fmt)+"/to/"+tend.toFormattedDate(fmt);
		String target=path+ds+"/"+year+"/"+id+"/"+id;
		
		JSONObject  reP = new JSONObject();
		reP.put("dataset" , "era5");
		reP.put("date"    , date);
		reP.put("stream"  , "oper");
		reP.put("repres" , "ll");	// sh/ll/gg
		reP.put("levtype" , "pl");	// ml/sfc/pl/pv/pt/dp
		reP.put("levelist" , "850/200");
		reP.put("param"   , "u/v/t");	//
		reP.put("step"    , "0");
		reP.put("time"    , "00:00:00/01:00:00/02:00:00/03:00:00/04:00:00/05:00:00/"+
							"06:00:00/07:00:00/08:00:00/09:00:00/10:00:00/11:00:00/"+
							"12:00:00/13:00:00/14:00:00/15:00:00/16:00:00/17:00:00/"+
							"18:00:00/19:00:00/20:00:00/21:00:00/22:00:00/23:00:00");
		reP.put("type"    , "an");	// an/fc
		reP.put("resol"   , "av");
		reP.put("grid"    , "0.3/0.3");
		if(basin==Basin.NAT)
			reP.put("area"    , "78/231/-9/357"); // north/west/south/east
		else if(basin==Basin.WNP)
			reP.put("area"    , "78/81/-9/210"); // north/west/south/east
		else if(basin==Basin.SIO)
			reP.put("area"    , "9/0/-78/111"); // north/west/south/east
		reP.put("format"  , "netcdf");
		reP.put("target"  , target+"Pl.nc");
		
		JSONObject  reS = new JSONObject();
		reS.put("dataset" , "era5");
		reS.put("date"    , date);
		reS.put("stream"  , "oper");
		reS.put("repres" , "ll");	// sh/ll/gg
		reS.put("levtype" , "sfc");	// ml/sfc/pl/pv/pt/dp
		reS.put("param"   , "sst");	//
		reS.put("step"    , "0");
		reP.put("time"    , "00:00:00/01:00:00/02:00:00/03:00:00/04:00:00/05:00:00/"+
							"06:00:00/07:00:00/08:00:00/09:00:00/10:00:00/11:00:00/"+
							"12:00:00/13:00:00/14:00:00/15:00:00/16:00:00/17:00:00/"+
							"18:00:00/19:00:00/20:00:00/21:00:00/22:00:00/23:00:00");
		reS.put("type"    , "an");	// an/fc
		reS.put("resol"   , "av");
		reS.put("grid"    , "0.3/0.3");
		if(basin==Basin.NAT)
			reS.put("area"    , "78/231/-9/357"); // north/west/south/east
		else if(basin==Basin.WNP)
			reS.put("area"    , "78/81/-9/210"); // north/west/south/east
		else if(basin==Basin.SIO)
			reS.put("area"    , "9/0/-78/111"); // north/west/south/east
		reS.put("format"  , "netcdf");
		reS.put("target"  , target+"Sfc.nc");
		
		return new JSONObject[]{reP,reS};
	}
	
	static void extractDataInterim(Typhoon ty){
		MDate tstr=new MDate(ty.getTime(0));
		
		int year=tstr.getYear();
		int offset=0;
		
		if     (tstr.getHour()==0 ) offset=1;
		else if(tstr.getHour()==6 ) offset=2;
		else if(tstr.getHour()==12) offset=3;
		else if(tstr.getHour()==18) offset=4;
		else throw new IllegalArgumentException("invalid hour for "+tstr);
		
		String npath=path+ds+"/"+year+"/"+ty.getID()+"/";
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'sdfopen "+npath+ty.getID()+"Pl.nc'\n");
		sb.append("'sdfopen "+npath+ty.getID()+"Sfc.nc'\n");
		sb.append("'set gxout fwrite'\n");
		sb.append("'set fwrite "+npath+ty.getID()+"_"+interpRes+".dat'\n\n");
		
		//sb.append("'set x 1 480'\n\n");
		
		sb.append("tt="+offset+"\n");
		sb.append("while(tt<="+(ty.getTCount()-1+offset)+")\n");
		sb.append("'set t 'tt\n");
		sb.append("'d sst.2(z=1)'\n");
		sb.append("'d u(lev=850)'\n");
		sb.append("'d u(lev=200)'\n");
		sb.append("'d v(lev=850)'\n");
		sb.append("'d v(lev=200)'\n");
		sb.append("'d T(lev=850)'\n");
		sb.append("'d T(lev=200)'\n");
		sb.append("tt=tt+1\n");
		sb.append("endwhile\n\n");
		
		sb.append("'disable fwrite'\n");
		sb.append("'close 2'\n");
		sb.append("'close 1'\n");
		sb.append("'reinit'\n");
		sb.append("'quit'\n");
		
		try(FileWriter fw=new FileWriter(npath+ty.getID()+"Ex.gs")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		sb=new StringBuilder();
		
		sb.append("dset ^"+ty.getID()+"_"+interpRes+".dat\n");
		sb.append("undef -9.99e8\n");
		sb.append("title "+ty.getID()+"\n");
		sb.append("xdef 173 linear  81 0.75\n");
		sb.append("ydef 117 linear  -9 0.75\n");
		sb.append("zdef   2 levels 850 200\n");
		sb.append("tdef "+ty.getTCount()+" linear "+new MDate(ty.getTime(0)).toGradsDate()+" 6hr\n");
		sb.append("vars 4\n");
		sb.append("sst 0 99 sea surface temperature (K)\n");
		sb.append("u   2 99 U velocity (m s**-1)\n");
		sb.append("v   2 99 V velocity (m s**-1)\n");
		sb.append("T   2 99 temperature (K)\n");
		sb.append("endvars\n");
		
		try(FileWriter fw=new FileWriter(npath+ty.getID()+"_"+interpRes+".ctl")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		OpenGrADS.runGS(npath+ty.getID()+"Ex.gs");
	}
	
	static void extractDataERA5(Typhoon ty){
		MDate tstr=new MDate(ty.getTime(0));
		
		int year=tstr.getYear();
		int offset=0;
		
		if     (tstr.getHour()==0 ) offset=1;
		else if(tstr.getHour()==6 ) offset=7;
		else if(tstr.getHour()==12) offset=13;
		else if(tstr.getHour()==18) offset=19;
		else throw new IllegalArgumentException("invalid hour for "+tstr);
		
		String npath=path+ds+"/"+year+"/"+ty.getID()+"/";
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'sdfopen "+npath+ty.getID()+"Pl.nc'\n");
		sb.append("'sdfopen "+npath+ty.getID()+"Sfc.nc'\n");
		sb.append("'set gxout fwrite'\n");
		sb.append("'set fwrite "+npath+ty.getID()+".dat'\n\n");
		
		//sb.append("'set x 1 1200'\n\n");
		
		sb.append("tt="+offset+"\n");
		sb.append("while(tt<="+((ty.getTCount()-1)*6+offset)+")\n");
		sb.append("tsst=math_int((tt-1)/24)+1\n");
		sb.append("'set t 'tt\n");
		sb.append("'d sst.2(z=1,t='tsst')'\n");
		sb.append("'d u(lev=850)'\n");
		sb.append("'d u(lev=200)'\n");
		sb.append("'d v(lev=850)'\n");
		sb.append("'d v(lev=200)'\n");
		sb.append("'d T(lev=850)'\n");
		sb.append("'d T(lev=200)'\n");
		sb.append("tt=tt+1\n");
		sb.append("endwhile\n\n");
		
		sb.append("'disable fwrite'\n");
		sb.append("'close 2'\n");
		sb.append("'close 1'\n");
		sb.append("'reinit'\n");
		sb.append("'quit'\n");
		
		try(FileWriter fw=new FileWriter(npath+ty.getID()+"Ex.gs")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		sb=new StringBuilder();
		
		sb.append("dset ^"+ty.getID()+".dat\n");
		sb.append("undef -9.99e8\n");
		sb.append("title "+ty.getID()+"\n");
		sb.append("xdef 1200 linear   0 0.3\n");
		sb.append("ydef  601 linear -90 0.3\n");
		sb.append("zdef   2 levels 850 200\n");
		sb.append("tdef "+((ty.getTCount()-1)*6+1)+" linear "+new MDate(ty.getTime(0)).toGradsDate()+" 1hr\n");
		sb.append("vars 4\n");
		sb.append("sst 0 99 sea surface temperature (K)\n");
		sb.append("u   2 99 U velocity (m s**-1)\n");
		sb.append("v   2 99 V velocity (m s**-1)\n");
		sb.append("T   2 99 temperature (K)\n");
		sb.append("endvars\n");
		
		try(FileWriter fw=new FileWriter(npath+ty.getID()+".ctl")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		OpenGrADS.runGS(npath+ty.getID()+"Ex.gs");
	}
	
	static void computingIndexInterim(Typhoon tr,int times){ // times = 2 means 6hr interval becomes 3hr
		if(times<1) throw new IllegalArgumentException("times should be at least 1");
		
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		if(times>1){
			DiagnosisFactory df=DiagnosisFactory.parseFile(npath+tr.getID()+"_6.ctl");
			DataDescriptor dd=df.getDataDescriptor();
			
			DataInterpolation di=new DataInterpolation(dd);
			di.temporalInterp(npath+tr.getID()+"_"+interpRes+".dat",Type.LINEAR,(dd.getTCount()-1)*times+1);
		}
		
		DiagnosisFactory df=DiagnosisFactory.parseFile(npath+tr.getID()+"_"+interpRes+".ctl");
		DataDescriptor dd=df.getDataDescriptor();
		
		String rng=domain+";"+tr.getTRange();
		
		Range r=new Range(rng,dd);
		Variable[] wind=df.getVariables(r,"u","v","T");
		
		/*** computing horizontal indices ***/
		Variable[] idx1=IndexInSC.c2DHorizontalIndex(dd,rng,tr,0.3f,19,72,9,18,"REFC","PEFC","AEFC","ISB","ETA","ULFI","htHFC");
		
		for(Variable v:ArrayUtil.concatAll(Variable.class,wind,idx1)) v.setUndef(wind[0].getUndef());
		for(Variable v:idx1) v.setName(v.getName()+"srf");
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,npath+"index_"+interpRes+".dat");
		dw.writeData(dd,ArrayUtil.concatAll(Variable.class,wind,idx1));	dw.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,npath+"intensity_"+interpRes+".dat");
		dw.writeData(dd,AccessBestTrack.toIntensityVariables(tr));	dw.closeFile();
		
		IOUtil.replaceContent(npath+"intensity_"+interpRes+".ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
		
		/*** computing along-track diagnostics ***/
		DiagnosisFactory df2=DiagnosisFactory.parseContent(tr.toCSMString(npath+tr.getID()+"_"+interpRes+".ctl",72,19,2,0.3f,-650,850));
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
		dm.cStormRelativeAziRadVelocity(tr.getUVel(),tr.getVVel(),utvr[0],utvr[1]);
		
		Variable utm =utvr[0].anomalizeX();	utvr[1].anomalizeX();
		Variable REFC=dm.cREFC(utvr[0],utvr[1]).averageAlong(Dimension.Y, 9,18);	// 300-600 km
		Variable ETA =dm.cMeanAbsoluteVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable zeta=dm.cMeanRelativeVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable fm  =dm.cMeanPlanetaryVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable ISB =dm.cMeanInertialStabilityByUT(utm).averageAlong(Dimension.Y, 9,18);
		Variable ULFI=REFC.plus(PEFC).divideEq(ETA).divideEq(86400f); ULFI.setName("ULFI");
		Variable mpi =tdm.cMPIWNP(sstm);
		
		dw=DataIOFactory.getDataWrite(dd,npath+"alongTrackDiags_"+interpRes+".dat");
		dw.writeData(dd,new Variable[]{sstm,vwsm,REFC,PEFC,ETA,zeta,fm,ISB,ULFI,mpi});	dw.closeFile();
		
		IOUtil.replaceContent(npath+"alongTrackDiags_"+interpRes+".ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
	}
	
	static void computingIndexERA5(Typhoon tr){ // times = 2 means 6hr interval becomes 3hr
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		DiagnosisFactory df=DiagnosisFactory.parseFile(npath+tr.getID()+".ctl");
		DataDescriptor dd=df.getDataDescriptor();
		
		String rng=domain+";"+tr.getTRange();
		
		Range r=new Range(rng,dd);
		Variable[] wind=df.getVariables(r,"u","v","T");
		
		/*** computing horizontal indices ***/
		Variable[] idx1=IndexInSC.c2DHorizontalIndex(dd,rng,tr,0.3f,19,72,9,18,"REFC","PEFC","AEFC","ISB","ETA","ULFI","htHFC");
		
		for(Variable v:ArrayUtil.concatAll(Variable.class,wind,idx1)) v.setUndef(wind[0].getUndef());
		for(Variable v:idx1) v.setName(v.getName()+"srf");
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,npath+"index.dat");
		dw.writeData(dd,ArrayUtil.concatAll(Variable.class,wind,idx1));	dw.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,npath+"intensity.dat");
		dw.writeData(dd,AccessBestTrack.toIntensityVariables(tr));	dw.closeFile();
		
		IOUtil.replaceContent(npath+"intensity.ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
		
		/*** computing along-track diagnostics ***/
		DiagnosisFactory df2=DiagnosisFactory.parseContent(tr.toCSMString(npath+tr.getID()+".ctl",72,19,2,0.3f,-650,850));
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
		dm.cStormRelativeAziRadVelocity(tr.getUVel(),tr.getVVel(),utvr[0],utvr[1]);
		
		Variable utm =utvr[0].anomalizeX();	utvr[1].anomalizeX();
		Variable REFC=dm.cREFC(utvr[0],utvr[1]).averageAlong(Dimension.Y, 9,18);	// 300-600 km
		Variable ETA =dm.cMeanAbsoluteVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable zeta=dm.cMeanRelativeVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable fm  =dm.cMeanPlanetaryVorticity(utm).averageAlong(Dimension.Y, 9,18);
		Variable ISB =dm.cMeanInertialStabilityByUT(utm).averageAlong(Dimension.Y, 9,18);
		Variable ULFI=REFC.plus(PEFC).divideEq(ETA).divideEq(86400f); ULFI.setName("ULFI");
		Variable mpi =tdm.cMPIWNP(sstm);
		
		dw=DataIOFactory.getDataWrite(dd,npath+"alongTrackDiags.dat");
		dw.writeData(dd,new Variable[]{sstm,vwsm,REFC,PEFC,ETA,zeta,fm,ISB,ULFI,mpi});	dw.closeFile();
		
		IOUtil.replaceContent(npath+"alongTrackDiags.ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
	}
	
	static void generatePlotGS(Typhoon tr){
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'open "+npath+"index_"+interpRes+".ctl'\n");
		sb.append("'open "+npath+"intensity_"+interpRes+".ctl'\n");
		sb.append("'open "+npath+tr.getID()+"_"+interpRes+".ctl'\n");
		sb.append("'open "+npath+"alongTrackDiags_"+interpRes+".ctl'\n");
		sb.append("'enable print "+npath+"index_"+interpRes+".gmf'\n\n");
		
		sb.append("lons=\"");
		for(int l=0,L=tr.getTCount();l<L;l++) sb.append(tr.getXPosition(l)+" ");
		sb.append("\"\nlats=\"");
		for(int l=0,L=tr.getTCount();l<L;l++) sb.append(tr.getYPosition(l)+" ");
		sb.append("\"\n\n");
		
		sb.append("'set rgb 16   0   0 255'\n");
		sb.append("'set rgb 17  55  55 255'\n");
		sb.append("'set rgb 18 110 110 255'\n");
		sb.append("'set rgb 19 165 165 255'\n");
		sb.append("'set rgb 20 220 220 255'\n\n");
		
		sb.append("'set rgb 21 255 220 220'\n");
		sb.append("'set rgb 22 255 165 165'\n");
		sb.append("'set rgb 23 255 110 110'\n");
		sb.append("'set rgb 24 255  55  55'\n");
		sb.append("'set rgb 25 255   0   0'\n\n");
		
		sb.append("'set grads off'\n");
		sb.append("'set mpdset mres'\n");
		sb.append("'set map 15 1 11'\n\n");
		
		sb.append("cc=1\n");
		sb.append("tt=1\n");
		sb.append("while(tt<="+tr.getTCount()+")\n");
		sb.append("'set t 'tt\n");
		sb.append("'q time'\n");
		sb.append("time=subwrd(result,3)\n");
		sb.append("'set lev 200'\n");
		sb.append("'setvpage 2 2 2 1'\n");
		sb.append("'set parea 0.7 10 0 8.5'\n");
		sb.append("'setlopts 7 0.2 10 10'\n");
		sb.append("'set grid off'\n");
		sb.append("'set gxout shaded'\n");
		sb.append("'set clevs -15 -12 -9 -6 -3 3 6 9 12 15'\n");
		sb.append("'set ccols 16 17 18 19 20 0 21 22 23 24 25'\n");
		sb.append("'d aefcsrf/(etasrf*86400)'\n");
		sb.append("'set gxout contour'\n");
		sb.append("'set rbrange 5 55'\n");
		sb.append("'set cthick 5'\n");
		sb.append("'set arrowhead -0.3'\n");
		sb.append("'set arrscl 0.5 80'\n");
		sb.append("'d skip(u,2);v'\n");
		sb.append("'cbarn 1 1 10.4 4.4'\n");
		sb.append("lon=subwrd(lons,cc)\n");
		sb.append("lat=subwrd(lats,cc)\n");
		sb.append("'pty ' lon' 'lat' 0.4 1 9'\n");
		sb.append("'drawtime "+tr.getName()+"("+tr.getID()+")'\n");
		sb.append("'set x 1'\n");
		sb.append("'set y 1'\n");
		sb.append("'set z 1'\n");
		sb.append("'d prs"+tr.getName()+".2(x=1,y=1,z=1)'\n");
		sb.append("prs=subwrd(result,4)\n\n");
		
		sb.append("'setvpage 2 2 1 1'\n");
		sb.append("'set parea 0.8 10 0 8.5'\n");
		sb.append("'setlopts 7 0.2 10 10'\n");
		sb.append("'set lev 200'\n");
		sb.append("'set grid off'\n");
		sb.append("'set gxout shaded'\n");
		sb.append("'set cmin 10'\n");
		sb.append("'set rbrange 0 60'\n");
		sb.append("'set cint 5'\n");
		sb.append("'d mag(u(lev=200)-u(lev=850),v(lev=200)-v(lev=850))'\n");
		sb.append("'cbarn 1 1 10.4 4.4'\n");
		sb.append("'pty ' lon' 'lat' 0.4 1 9'\n");
		sb.append("'draw title vertical wind shear (200hPa-850hPa)'\n\n");
		
		sb.append("'setvpage 2 2 1 2'\n");
		sb.append("'set parea 0.8 10 0 8.5'\n");
		sb.append("'setlopts 7 0.2 10 10'\n");
		sb.append("'set lev 200'\n");
		sb.append("'set grid off'\n");
		sb.append("'set gxout shaded'\n");
		sb.append("'set clevs 9 12 15 18 21 24 27 28 28.5 29 29.5 30 30.5 31'\n");
		sb.append("'d sst.3-273.15'\n");
		sb.append("'cbarn 1 1 10.4 4.4'\n");
		sb.append("'pty ' lon' 'lat' 0.4 1 9'\n");
		sb.append("'draw title sea surface temperature'\n\n");
		
		sb.append("'setvpage 2 2 2 2'\n");
		sb.append("'set parea 0.8 9.3 1.2 7.25'\n");
		sb.append("'setlopts 7 0.2 1 10'\n");
		sb.append("'set grid on'\n");
		sb.append("'set x 1'\n");
		sb.append("'set y 1'\n");
		sb.append("'set lev 200'\n");
		sb.append("'set t 1 "+tr.getTCount()+"'\n");
		sb.append("'set cthick 12'\n");
		sb.append("'set cmark 5'\n");
		sb.append("'set digsize 0.1'\n");
		sb.append("'set vrange 920 1010'\n");
		sb.append("'d prs"+tr.getName()+".2(x=1,y=1,z=1)'\n");
		sb.append("'q w2xy 'time' 'prs\n");
		sb.append("xx=subwrd(result,3)\n");
		sb.append("yy=subwrd(result,6)\n");
		sb.append("'draw mark 3 'xx' 'yy' 0.3'\n");
		sb.append("'set vrange 25 31'\n");
		sb.append("'set ylpos 0 r'\n");
		sb.append("'set ylint 1'\n");
		sb.append("'set ccolor 2 1 4'\n");
		sb.append("'set cmark 0'\n");
		sb.append("'d sst.4(x=1,y=1,z=1)'\n");
		sb.append("'set vrange 0 36'\n");
		sb.append("'set ylpos 0.55 r'\n");
		sb.append("'set ylint 3'\n");
		sb.append("'set ccolor 4 1 4'\n");
		sb.append("'set cmark 0'\n");
		sb.append("'d vws.4(x=1,y=1,z=1)'\n");
		sb.append("'set vrange -6 6'\n");
		sb.append("'set ylpos 1.1 r'\n");
		sb.append("'set ylint 1'\n");
		sb.append("'set ccolor 3 1 4'\n");
		sb.append("'set cmark 0'\n");
		sb.append("'d ulfi.4(x=1,y=1,lev=200)'\n");
		sb.append("'draw title along-track vars'\n");
		sb.append("'set strsiz 0.16'\n");
		sb.append("'set string 2'\n");
		sb.append("'draw string 9.3 7.6 SST'\n");
		sb.append("'set string 4'\n");
		sb.append("'draw string 9.9 7.6 VWS'\n");
		sb.append("'set string 3'\n");
		sb.append("'draw string 10.5 7.6 ULFI'\n\n");
		
		sb.append("'print'\n");
		sb.append("'c'\n");
		sb.append("cc=cc+1\n");
		sb.append("tt=tt+1\n");
		sb.append("endwhile\n\n");
		
		sb.append("'disable print'\n");
		sb.append("'close 4'\n");
		sb.append("'close 3'\n");
		sb.append("'close 2'\n");
		sb.append("'close 1'\n");
		sb.append("'reinit'\n");
		
		try(FileWriter fw=new FileWriter(npath+"index_"+interpRes+".gs")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		//OpenGrADS.runGS(npath+"index.gs");
	}
	
	static void generateAnimateGS(Typhoon tr){
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		StringBuffer sb=new StringBuffer();
		
		sb.append("'open "+npath+"index_"+interpRes+".ctl'\n");
		sb.append("'open "+npath+"intensity_"+interpRes+".ctl'\n\n");
		
		sb.append("lons=\""); for(int l=0;l<tr.getTCount();l++) sb.append(tr.getXPositions()[l]+" "); sb.append("\"\n");
		sb.append("lats=\""); for(int l=0;l<tr.getTCount();l++) sb.append(tr.getYPositions()[l]+" "); sb.append("\"\n");
		sb.append("tend="+tr.getTCount()+"\n\n");
		
		sb.append("'set rgb 16   0   0 255'\n");
		sb.append("'set rgb 17  55  55 255'\n");
		sb.append("'set rgb 18 110 110 255'\n");
		sb.append("'set rgb 19 165 165 255'\n");
		sb.append("'set rgb 20 220 220 255'\n\n");
		sb.append("'set rgb 21 255 220 220'\n");
		sb.append("'set rgb 22 255 165 165'\n");
		sb.append("'set rgb 23 255 110 110'\n");
		sb.append("'set rgb 24 255  55  55'\n");
		sb.append("'set rgb 25 255   0   0'\n\n");
		
		sb.append("cc=1\n");
		sb.append("tt=1\n");
		sb.append("while(tt<=tend)\n");
		sb.append("'reset'\n");
		sb.append("'set grads off'\n");
		sb.append("'set mpdset mres'\n");
		sb.append("'set map 5 1 7'\n");
		sb.append("'set t 'tt\n");
		sb.append("'q time'\n");
		sb.append("tim=subwrd(result,3)\n");
		sb.append("'setvpage 1.6 2 1.6 1.04'\n");
		sb.append("'setlopts 7 0.2 10 10'\n");
		sb.append("'set grid off'\n");
		sb.append("'set gxout shaded'\n");
		sb.append("'color -gxout shaded -kind blue->white->red -levs -13 -9 -6 -4 -3 3 4 6 9 13'\n");
		sb.append("'d ULFIsrf'\n");
		sb.append("'set cthick 8'\n");
		sb.append("'set ccolor 27'\n");
		sb.append("'set arrowhead -0.35'\n");
		sb.append("'set arrscl 0.6 90'\n");
		sb.append("'set arrlab off'\n");
		sb.append("'d skip(maskout(u,20-mag(u,v)),2);v'\n");
		sb.append("'set cthick 9'\n");
		sb.append("'set ccolor 1'\n");
		sb.append("'set arrowhead -0.35'\n");
		sb.append("'set arrscl 0.6 150'\n");
		sb.append("'set arrlab off'\n");
		sb.append("'d skip(maskout(u,mag(u,v)-20),2);v'\n");
		sb.append("'cbarn 1 0 4.4 0.18'\n");
		sb.append("lon=subwrd(lons,cc)\n");
		sb.append("lat=subwrd(lats,cc)\n");
		sb.append("'pty ' lon' 'lat' 0.5 1 9'\n");
		sb.append("'drawtime'\n");
		sb.append("'set x 1'\n");
		sb.append("'set y 1'\n");
		sb.append("'set z 1'\n");
		sb.append("'d prs"+tr.getName()+".2(x=1,y=1,z=1)'\n");
		sb.append("prs=subwrd(result,4)\n\n");
		
		sb.append("'setvpage 1.6 2 1.6 1.83'\n");
		sb.append("'setlopts 7 0.2 1 4'\n");
		sb.append("'set grid on'\n");
		sb.append("'set x 1'\n");
		sb.append("'set y 1'\n");
		sb.append("'set z 1'\n");
		sb.append("'set cthick 7'\n");
		sb.append("'set t 1 'tend\n");
		sb.append("'set cmark 0'\n");
		sb.append("'d prs"+tr.getName()+".2(x=1,y=1,z=1)'\n");
		sb.append("'q w2xy 'tim' 'prs\n");
		sb.append("xx=subwrd(result,3)\n");
		sb.append("yy=subwrd(result,6)\n");
		sb.append("'draw mark 3 'xx' 'yy' 0.3'\n");
		sb.append("'draw title minimum central pressure'\n");
		sb.append("ttt=tt+100\n");
		sb.append("'printim "+npath+"animation_"+interpRes+"'ttt'.png white x1600 y1200'\n");
		sb.append("'c'\n");
		sb.append("cc=cc+1\n");
		sb.append("tt=tt+1\n");
		sb.append("endwhile\n\n");
		
		sb.append("'disable print'\n");
		sb.append("'close 2'\n");
		sb.append("'close 1'\n");
		sb.append("'reinit'\n\n");
		
		sb.append("'!magick convert -delay 8 -crop 1400x735+50+20 +repage "+npath+"animation_"+
			interpRes+"*.png "+npath+"animation_"+interpRes+".gif'\n");
		sb.append("'!rm "+npath+"animation_"+interpRes+"*.png'\n");
		
		try{
			FileWriter fw=new FileWriter(new File(npath+"animation_"+interpRes+".gs"));
			fw.write(sb.toString());	fw.close();
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
}
