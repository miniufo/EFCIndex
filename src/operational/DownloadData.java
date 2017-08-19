//
package operational;

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


//
public final class DownloadData{
	//
	static boolean prsData=false;
	
	static final Predicate<Typhoon> cond=ty->{
		int year=new MDate(ty.getTime(0)).getYear();
		//return year==1985&&ty.getName().equalsIgnoreCase("Elena");
		return year==2016&&(Integer.parseInt(ty.getID())==1619);
	};
	
	static final DataSets ds=DataSets.JMA;
	static final String path="D:/Data/ULFI/";
	
	static final List<Typhoon> all=AccessBestTrack.getTyphoons("d:/Data/Typhoons/"+ds+"/"+ds+".txt","",ds);
	
	private static final DateTimeFormatter fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.ENGLISH);
	
	
	//
	public static void main(String[] args){
		PythonInterpreter interp=new PythonInterpreter();
		interp.execfile("D:/Java/ecmwf-api-client-python/ecmwfapi/api.py");
		interp.exec("parseFunc = json.loads");
		interp.exec("server = ECMWFDataServer()");
		interp.exec("retrieve = server.retrieve");
		
		all.stream().filter(cond).forEach(ty->{
			// downloading NetCDF data
			//JythonDownload(interp,prepareJSONData(ty));
			
			// extract NetCDF data into binary data
			//extractData(ty);
			
			// computing index
			computingIndex(ty);
			
			// generate GS and plot
			generateGS(ty);
		});
		
		interp.close();
	}
	
	static JSONObject[] prepareJSONData(Typhoon ty){
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
		reS.put("format"  , "netcdf");
		reS.put("target"  , target+"Sfc.nc");
		
		return new JSONObject[]{reP,reS};
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
	
	static void extractData(Typhoon ty){
		MDate tstr=new MDate(ty.getTime(0));
		
		int year=tstr.getYear();
		int offset=0;
		
		if(tstr.getHour()==0 ) offset=1;
		else if(tstr.getHour()==6 ) offset=2;
		else if(tstr.getHour()==12) offset=3;
		else if(tstr.getHour()==18) offset=4;
		else throw new IllegalArgumentException("invalid hour for "+tstr);
		
		String npath=path+ds+"/"+year+"/"+ty.getID()+"/";
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'sdfopen "+npath+ty.getID()+"Pl.nc'\n");
		sb.append("'sdfopen "+npath+ty.getID()+"Sfc.nc'\n");
		sb.append("'set gxout fwrite'\n");
		sb.append("'set fwrite "+npath+ty.getID()+".dat'\n\n");
		
		sb.append("'set x 1 480'\n\n");
		
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
		
		sb.append("dset ^"+ty.getID()+".dat\n");
		sb.append("undef -9.99e8\n");
		sb.append("title "+ty.getID()+"\n");
		sb.append("xdef 480 linear   0 0.75\n");
		sb.append("ydef 241 linear -90 0.75\n");
		sb.append("zdef   2 levels 850 200\n");
		sb.append("tdef "+ty.getTCount()+" linear "+new MDate(ty.getTime(0)).toGradsDate()+" 6hr\n");
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
	
	static void computingIndex(Typhoon tr){
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		DiagnosisFactory df=DiagnosisFactory.parseFile(npath+tr.getID()+".ctl");
		DataDescriptor dd=df.getDataDescriptor();
		
		String rng="lon(90,190);lat(2,51);"+tr.getTRange();
		
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
		dm.cStormRelativeAziRadVelocity(tr.getZonalVelocity(),tr.getMeridionalVelocity(),utvr[0],utvr[1]);
		
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
	
	static void generateGS(Typhoon tr){
		int year=new MDate(tr.getTime(0)).getYear();
		
		String npath=path+ds+"/"+year+"/"+tr.getID()+"/";
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'open "+npath+"index.ctl'\n");
		sb.append("'open "+npath+"intensity.ctl'\n");
		sb.append("'open "+npath+tr.getID()+".ctl'\n");
		sb.append("'open "+npath+"alongTrackDiags.ctl'\n");
		sb.append("'enable print "+npath+"index.gmf'\n\n");
		
		sb.append("lons=\"");
		for(int l=0,L=tr.getTCount();l<L;l++) sb.append(tr.getLongitude(l)+" ");
		sb.append("\"\nlats=\"");
		for(int l=0,L=tr.getTCount();l<L;l++) sb.append(tr.getLatitude(l)+" ");
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
		sb.append("'set lon 90 180'\n");
		sb.append("'set lat 2 51'\n");
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
		sb.append("'set lon 90 180'\n");
		sb.append("'set lat 2 51'\n");
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
		sb.append("'set lon 90 180'\n");
		sb.append("'set lat 2 51'\n");
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
		//sb.append("'quit'\n");
		
		try(FileWriter fw=new FileWriter(npath+"index.gs")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		//OpenGrADS.runGS(npath+"index.gs");
	}
}
