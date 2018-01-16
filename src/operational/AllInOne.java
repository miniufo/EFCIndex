//
package operational;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;
import miniufo.application.advanced.CoordinateTransformation;
import miniufo.application.basic.DynamicMethodsInCC;
import miniufo.application.basic.IndexInSC;
import miniufo.application.basic.ThermoDynamicMethodsInCC;
import miniufo.basic.ArrayUtil;
import miniufo.basic.InterpolationModel.Type;
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
import miniufo.io.CsmDataWriteStream;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;
import miniufo.io.IOUtil;
import miniufo.lagrangian.Record;
import miniufo.lagrangian.Typhoon;
import miniufo.util.DataInterpolation;

//
public final class AllInOne{
	//
	private static final DateTimeFormatter fmt=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withLocale(Locale.ENGLISH);
	
	private static String workpath      =null;
	private static String gridDataFname =null;
	private static String TCfname       =null;
	private static String startTimeBJ   =null;
	private static String range         =null;
	private static String azimuthGrids  =null;
	private static String radialGrids   =null;
	private static String radialInterval=null;
	private static String averagingBand =null;
	private static String SSTAveRadius  =null;
	private static String VWSAveRadius  =null;
	
	private static int xgrid=0;	// azimuth grid count
	private static int ygrid=0;	// radial  grid count
	private static int ystr =0;	// ytag for index ave
	private static int yend =0;	// ytag for index ave
	private static int ysst =0; // ytag for SST ave
	private static int yvws =0; // ytag for VWS ave
	private static float dy =0;	// radial grid spacing (degree)
	private static long UTC =0;	// start time (UTC)
	
	private static final boolean debug=true;
	
	
	//
	public static void main(String[] args){
		/*
		if(args==null||args.length==0){
			System.out.println("usage:");
			System.out.println("java -jar Index.jar d:/Data/RuntimeParams.json");
			System.exit(0);
		}
		
		JSONObject params=readParameterFile(args[0]);*/
		JSONObject params=readParameterFile("d:/Data/ULFI/output/RuntimeParams.json");
		
		workpath      =params.getString("working directory");
		gridDataFname =params.getString("grid data file"   ); gridDataFname=interpGridData();
		TCfname       =params.getString("TC file"          );
		startTimeBJ   =params.getString("starting time BJ" );
		range         =params.getString("range"            );
		azimuthGrids  =params.getString("azimuth grids"    );
		radialGrids   =params.getString("radial  grids"    );
		radialInterval=params.getString("radial interval"  );
		averagingBand =params.getString("index ave band"   );
		SSTAveRadius  =params.getString("SST ave radius"   );
		VWSAveRadius  =params.getString("VWS ave radius"   );
		
		String[] tokens=averagingBand.split("-");
		
		xgrid=Integer.parseInt(azimuthGrids);
		ygrid=Integer.parseInt( radialGrids);
		dy   =Float.parseFloat(radialInterval.replaceAll("-deg",""));
		ystr =Math.round(Float.parseFloat(   tokens[0].replaceAll("km",""))/(dy*111.1984154f));
		yend =Math.round(Float.parseFloat(   tokens[1].replaceAll("km",""))/(dy*111.1984154f));
		ysst =Math.round(Float.parseFloat(SSTAveRadius.replaceAll("km",""))/(dy*111.1984154f));
		yvws =Math.round(Float.parseFloat(VWSAveRadius.replaceAll("km",""))/(dy*111.1984154f));
		UTC  =new MDate(Long.parseLong(startTimeBJ+"0000")).add("-8hr").getLongTime();
		
		if(debug){
			System.out.println("working directory: "+workpath     );
			System.out.println("grid data file   : "+gridDataFname);
			System.out.println("TC file          : "+TCfname      );
			System.out.println("starting time BJ : "+startTimeBJ  );
			System.out.println("UTC              : "+UTC          );
			System.out.println("range            : "+range        );
			System.out.println("azimuth grids    : "+xgrid        );
			System.out.println("radial  grids    : "+ygrid        );
			System.out.println("radial interval  : "+dy           );
			System.out.println("index ave tags   : "+ystr+"-"+yend);
			System.out.println("SST ave radius   : "+ysst         );
			System.out.println("VWS ave radius   : "+yvws         );
			System.out.println();
		}
		
		Typhoon ty=new TyphoonCMA(workpath+TCfname).getAsTyphoon(startTimeBJ+"0000");
		
		if(debug) System.out.println(ty);
		
		computeCase(ty);
		generateGS(ty);
	}
	
	static String interpGridData(){
		DiagnosisFactory df=DiagnosisFactory.parseFile(workpath+gridDataFname); df.setPrinting(false);
		DataDescriptor dd=df.getDataDescriptor();
		
		int dt=Math.round(dd.getDTDef()[0]);
		
		if(dt==3600*6) return gridDataFname;
		
		if(dt<3600*6)
		throw new IllegalArgumentException("deltaT of grid data file ("+dd.getDTDef()[0]/3600+") should be equal or larger than 6hr");
		
		if(dt%(3600*6)!=0)
		throw new IllegalArgumentException("deltaT of grid data file should be multiple of 6hr");
		
		if(dt*(dd.getTCount()-1)%(3600*6)!=0)
		throw new IllegalArgumentException("deltaT of grid data file cannot be divided by 6hr");
		
		int ndt=dt*(dd.getTCount()-1)/(3600*6)+1;
		
		String[] parts=gridDataFname.split("\\.");
		
		String interpFile=parts[0]+"_interp.dat";
		
		DataInterpolation di=new DataInterpolation(dd);
		di.temporalInterp(workpath+interpFile,Type.LINEAR,ndt);
		
		return parts[0]+"_interp.ctl";
	}
	
	static void computeCase(Typhoon tr){
		DiagnosisFactory df=DiagnosisFactory.parseFile(workpath+gridDataFname); df.setPrinting(false);
		DataDescriptor dd=df.getDataDescriptor();
		
		String rng=range+";"+tr.getTRange();
		
		Range r=new Range(rng,dd);
		Variable[] wind=df.getVariables(r,"u","v");
		
		/*** computing horizontal indices ***/
		Variable[] idx1=IndexInSC.c2DHorizontalIndex(dd,rng,tr,dy,ygrid,xgrid,ystr,yend,"REFC","PEFC","ETA","ULFI");
		
		for(Variable v:ArrayUtil.concatAll(Variable.class,wind,idx1)) v.setUndef(wind[0].getUndef());
		for(Variable v:idx1) v.setName(v.getName()+"srf");
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,workpath+"index.dat"); dw.setPrinting(false);
		dw.writeData(dd,ArrayUtil.concatAll(Variable.class,wind,idx1));	dw.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,workpath+"intensity.dat"); dw.setPrinting(false);
		dw.writeData(dd,AccessBestTrack.toIntensityVariables(tr));	dw.closeFile();
		
		IOUtil.replaceContent(
			workpath+"intensity.ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
		
		
		/*** computing along-track diagnostics ***/
		DiagnosisFactory df2=DiagnosisFactory.parseContent(tr.toCSMString(workpath+gridDataFname,xgrid,ygrid,2,dy,-650,850));
		CsmDescriptor csd=(CsmDescriptor)df2.getDataDescriptor();
		
		CylindricalSpatialModel csm=new CylindricalSpatialModel(csd);
		DynamicMethodsInCC dm=new DynamicMethodsInCC(csm);
		ThermoDynamicMethodsInCC tdm=new ThermoDynamicMethodsInCC(csm);
		CoordinateTransformation ct=new CoordinateTransformation(new SphericalSpatialModel(dd),csm);
		
		Variable[] vars=df2.getVariables(new Range("",csd),false,"u","v");
		Variable[] shrs=dm.cVerticalWindShear(vars[0],vars[1]);
		Variable   sst =df2.getVariables(new Range("z(1,1)",csd),false,"sst")[0];
		
		Variable shrsum=dm.cRadialAverage(shrs[0],1,yvws).anomalizeX();
		Variable shrsvm=dm.cRadialAverage(shrs[1],1,yvws).anomalizeX();
		
		Variable vwsm=shrsum.hypotenuse(shrsvm); vwsm.setName("vws");
		Variable sstm=dm.cRadialAverage(sst,1,ysst).anomalizeX().minusEq(273.15f);
		
		Variable[] utvr=ct.reprojectToCylindrical(vars[0],vars[1]);
		Variable Va=utvr[1].copy(); Va.anomalizeX();
		Variable PEFC=dm.cPEFC(Va).averageAlong(Dimension.Y,ystr,yend);
		
		dm.cStormRelativeAziRadVelocity(tr.getUVel(),tr.getVVel(),utvr[0],utvr[1]);
		
		Variable utm =utvr[0].anomalizeX();	utvr[1].anomalizeX();
		Variable REFC=dm.cREFC(utvr[0],utvr[1])     .averageAlong(Dimension.Y,ystr,yend);
		Variable ETA =dm.cMeanAbsoluteVorticity(utm).averageAlong(Dimension.Y,ystr,yend);
		Variable ULFI=REFC.plus(PEFC).divideEq(ETA).divideEq(86400f); ULFI.setName("ULFI");
		Variable mpi =tdm.cMPIWNP(sstm);
		
		CsmDataWriteStream cdws=new CsmDataWriteStream(workpath+"cylind.dat");
		cdws.writeData(csd,utvr[0],utvr[1],Va); cdws.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,workpath+"alongTrackDiags.dat"); dw.setPrinting(false);
		dw.writeData(dd,new Variable[]{sstm,vwsm,REFC,PEFC,ETA,ULFI,mpi});	dw.closeFile();
		
		IOUtil.replaceContent(
			workpath+"alongTrackDiags.ctl",
			dd.getTDef().getFirst().toGradsDate(),
			new MDate(tr.getTime(0)).toGradsDate()
		);
	}
	
	static JSONObject readParameterFile(String fname){
		String content=null;
		
		try(Stream<String> lines=Files.lines(Paths.get(fname))){
			Optional<String> op=lines.reduce((a,b)->a+"\n"+b);
			
			if(op.isPresent()) content=op.get();
			
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
		
		return new JSONObject(content);
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
			
			return ty.interpolateToDT(6*3600);
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
			MDate md=new MDate(Long.valueOf(predictTime)).add((Integer.parseInt(forecastHour)-8)+"hr");
			
			Record r=new Record(md.getLongTime(),lon,lat,4);
			
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
	
	static void generateGS(Typhoon tr){
		String[] tokens=range.split(";");
		String[] lonTKN=tokens[0].split(",");
		String[] latTKN=tokens[1].split(",");
		
		float lonstr=Float.parseFloat(lonTKN[0].replaceAll("lon\\(",""));
		float lonend=Float.parseFloat(lonTKN[1].replaceAll("\\)"   ,""));
		float latstr=Float.parseFloat(latTKN[0].replaceAll("lat\\(",""));
		float latend=Float.parseFloat(latTKN[1].replaceAll("\\)"   ,""));
		
		StringBuilder sb=new StringBuilder();
		
		sb.append("'open "+workpath+"index.ctl'\n");
		sb.append("'open "+workpath+"intensity.ctl'\n");
		sb.append("'open "+workpath+gridDataFname+"'\n");
		sb.append("'open "+workpath+"alongTrackDiags.ctl'\n");
		sb.append("'enable print "+workpath+"index.gmf'\n\n");
		
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
		sb.append("'set lon "+lonstr+" "+lonend+"'\n");
		sb.append("'set lat "+latstr+" "+latend+"'\n");
		sb.append("'set lev 200'\n");
		sb.append("'setvpage 2 2 2 1'\n");
		sb.append("'set parea 0.7 10 0 8.5'\n");
		sb.append("'setlopts 7 0.2 10 10'\n");
		sb.append("'set grid off'\n");
		sb.append("'set gxout shaded'\n");
		sb.append("'set clevs -15 -12 -9 -6 -3 3 6 9 12 15'\n");
		sb.append("'set ccols 16 17 18 19 20 0 21 22 23 24 25'\n");
		sb.append("'d (refcsrf+pefcsrf)/(etasrf*86400)'\n");
		sb.append("'set gxout contour'\n");
		sb.append("'set rbrange 5 55'\n");
		sb.append("'set cthick 5'\n");
		sb.append("'set arrowhead -0.3'\n");
		sb.append("'set arrscl 0.5 80'\n");
		sb.append("'d skip(u,4);v'\n");
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
		sb.append("'set lon "+lonstr+" "+lonend+"'\n");
		sb.append("'set lat "+latstr+" "+latend+"'\n");
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
		sb.append("'set lon "+lonstr+" "+lonend+"'\n");
		sb.append("'set lat "+latstr+" "+latend+"'\n");
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
		
		try(FileWriter fw=new FileWriter(workpath+"index.gs")){
			fw.write(sb.toString());
		}catch(IOException e){ e.printStackTrace(); System.exit(0);}
	}
}
