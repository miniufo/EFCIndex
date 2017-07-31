//
package pack;

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
import miniufo.diagnosis.Range;
import miniufo.diagnosis.SphericalSpatialModel;
import miniufo.diagnosis.Variable;
import miniufo.diagnosis.Variable.Dimension;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;
import miniufo.lagrangian.Typhoon;


//
public final class EFCIndex{
	//
	static TCCase Elena   =new TCCase();
	static TCCase Opal    =new TCCase();
	static TCCase Haima   =new TCCase();
	static TCCase Flo     =new TCCase();
	static TCCase Gene    =new TCCase();
	static TCCase Dora    =new TCCase();
	static TCCase Omar    =new TCCase();
	static TCCase Kirogi  =new TCCase();
	static TCCase Shanshan=new TCCase();
	static TCCase Rammasun=new TCCase();
	
	static{
		// for case Hurricane Elena (1985)
		Elena.name="Elena";
		Elena.cond="name="+Elena.name+";time=1Aug1985-30Sep1985";
		Elena.btdata="D:/Data/Typhoons/NHC/original/tracks1851to2008_atl_reanal.txt";
		Elena.eidata="D:/Data/DiagnosisVortex/"+Elena.name+"/EFCIndex/"+Elena.name+".ctl";
		Elena.range ="lon(235,315);lat(10,50);";
		Elena.output="d:/Data/DiagnosisVortex/"+Elena.name+"/EFCIndex/";
		Elena.dset=DataSets.NHC;
		
		// for case Hurricane Opal (1995)
		Opal.name="Opal";
		Opal.cond="name="+Opal.name+";time=1Sep1995-31Oct1995";
		Opal.btdata="D:/Data/Typhoons/NHC/original/tracks1851to2008_atl_reanal.txt";
		Opal.eidata="D:/Data/DiagnosisVortex/"+Opal.name+"/EFCIndex/"+Opal.name+".ctl";
		Opal.range ="lon(235,315);lat(10,50);";
		Opal.output="d:/Data/DiagnosisVortex/"+Opal.name+"/EFCIndex/";
		Opal.dset=DataSets.NHC;
		
		// for case Tropical Storm Haima (2004)
		Haima.name="Haima";
		Haima.cond="name="+Haima.name+";time=1Sep2004-30Sep2004";
		Haima.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Haima.eidata="D:/Data/DiagnosisVortex/"+Haima.name+"/EFCIndex/"+Haima.name+".ctl";
		Haima.range ="lon(80,160);lat(10,60);";
		Haima.output="d:/Data/DiagnosisVortex/"+Haima.name+"/EFCIndex/";
		Haima.dset=DataSets.JMA;
		
		// for case Tropical Cyclone Shanshan (2006)
		Shanshan.name="Shanshan";
		Shanshan.cond="name="+Shanshan.name+";time=1Sep2006-30Sep2006";
		Shanshan.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Shanshan.eidata="D:/Data/DiagnosisVortex/"+Shanshan.name+"/EFCIndex/"+Shanshan.name+".ctl";
		Shanshan.range ="lon(100,160);lat(10,50);";
		Shanshan.output="d:/Data/DiagnosisVortex/"+Shanshan.name+"/EFCIndex/";
		Shanshan.dset=DataSets.JMA;
		
		// for case Tropical Cyclone Dora (2007)
		Dora.name="Dora";
		Dora.cond="name="+Dora.name+";time=1Jan2007-28Feb2007";
		Dora.btdata="D:/Data/DiagnosisVortex/"+Dora.name+"/EFCIndex/JTWC.txt";
		Dora.eidata="D:/Data/DiagnosisVortex/"+Dora.name+"/EFCIndex/"+Dora.name+".ctl";
		Dora.range ="lon(35,95);lat(-35,5);";
		Dora.output="d:/Data/DiagnosisVortex/"+Dora.name+"/EFCIndex/";
		Dora.dset=DataSets.JTWC;
		
		// for case Tropical Cyclone Flo (1990)
		Flo.name="Flo";
		Flo.cond="name="+Flo.name+";time=1Sep1990-30Oct1990";
		Flo.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Flo.eidata="D:/Data/DiagnosisVortex/"+Flo.name+"/EFCIndex/"+Flo.name+".ctl";
		Flo.range ="lon(105,170);lat(10,50);";
		Flo.output="d:/Data/DiagnosisVortex/"+Flo.name+"/EFCIndex/";
		Flo.dset=DataSets.JMA;
		
		// for case Tropical Cyclone Gene (1990)
		Gene.name="Gene";
		Gene.cond="name="+Gene.name+";time=1Sep1990-30Sep1990";
		Gene.btdata="D:/Data/DiagnosisVortex/"+Gene.name+"/EFCIndex/JMA.txt";
		Gene.eidata="D:/Data/DiagnosisVortex/"+Gene.name+"/EFCIndex/"+Gene.name+".ctl";
		Gene.range ="lon(105,170);lat(10,50);";
		Gene.output="d:/Data/DiagnosisVortex/"+Gene.name+"/EFCIndex/";
		Gene.dset=DataSets.JMA;
		
		// for case Typhoon Omar (1992)
		Omar.name="Omar";
		Omar.cond="name="+Omar.name+";time=1Aug1992-30Sep1992";
		Omar.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Omar.eidata="D:/Data/DiagnosisVortex/"+Omar.name+"/EFCIndex/"+Omar.name+".ctl";
		Omar.range ="lon(100,165);lat(0,40);";
		Omar.output="d:/Data/DiagnosisVortex/"+Omar.name+"/EFCIndex/";
		Omar.dset=DataSets.JMA;
		
		// for case Typhoon Kirogi (2000)
		Kirogi.name="Kirogi";
		Kirogi.cond="name="+Kirogi.name+";time=1Jul2000-31Jul2000";
		Kirogi.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Kirogi.eidata="D:/Data/DiagnosisVortex/"+Kirogi.name+"/EFCIndex/"+Kirogi.name+".ctl";
		Kirogi.range ="lon(110,175);lat(10,50);";
		Kirogi.output="d:/Data/DiagnosisVortex/"+Kirogi.name+"/EFCIndex/";
		Kirogi.dset=DataSets.JMA;
		
		// for case Typhoon Rammasun (2014)
		Rammasun.name="Rammasun";
		Rammasun.cond="name="+Rammasun.name+";time=1Jul2014-31Jul2014";
		Rammasun.btdata="D:/Data/Typhoons/JMA/JMA.txt";
		Rammasun.eidata="D:/Data/DiagnosisVortex/"+Rammasun.name+"/EFCIndex/"+Rammasun.name+".ctl";
		Rammasun.range ="lon(100,175);lat(3,40);";
		Rammasun.output="d:/Data/DiagnosisVortex/"+Rammasun.name+"/EFCIndex/";
		Rammasun.dset=DataSets.JMA;
	}
	
	
	//
	public static void main(String[] args){
		//computeCase(Elena);
		//computeCase(Haima);
		//computeCase(Opal);
		//computeCase(Shanshan);
		//computeCase(Dora);
		//computeCase(Flo);
		//computeCase(Gene);
		//computeCase(Omar);
		//computeCase(Kirogi);
		computeCase(Rammasun);
	}
	
	private static void computeCase(TCCase tc){
		DiagnosisFactory df=DiagnosisFactory.parseFile(tc.eidata);
		DataDescriptor dd=df.getDataDescriptor();
		
		Typhoon tr=AccessBestTrack.getTyphoons(tc.btdata,tc.cond,tc.dset).get(0);
		System.out.println(tr.getName()+"\t"+tr.getBirthDateByWind(0));
		System.out.println(tr);
		
		Range r=new Range(tc.range+tr.getTRange(),dd);
		Variable[] wind=df.getVariables(r,"u","v","T");
		
		/*** computing horizontal indices ***/
		Variable[] idx1=IndexInSC.c2DHorizontalIndex(dd,tc.range+tr.getTRange(),tr,
		0.3f,19,72,"REFC","PEFC","AEFC","ISB","ETA","ULFI","htHFC");
		Variable[] idx2=IndexInSC.c2DHorizontalIndex(dd,tc.range+tr.getTRange(),0,-10,
		0.3f,19,72,"REFC","PEFC","AEFC","ISB","ETA","ULFI","htHFC");
		
		for(Variable v:ArrayUtil.concatAll(Variable.class,wind,idx1,idx2)) v.setUndef(wind[0].getUndef());
		for(Variable v:idx1) v.setName(v.getName()+"srf");
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,tc.output+"index.dat");
		dw.writeData(dd,ArrayUtil.concatAll(Variable.class,wind,idx1,idx2));	dw.closeFile();
		
		dw=DataIOFactory.getDataWrite(dd,tc.output+"intensity.dat");
		dw.writeData(dd,AccessBestTrack.toIntensityVariables(tr));	dw.closeFile();
		
		
		/*** computing along-track diagnostics ***/
		DiagnosisFactory df2=DiagnosisFactory.parseContent(tr.toCSMString(tc.eidata,72,19,2,0.3f,-650,850));
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
		
		dw=DataIOFactory.getDataWrite(dd,tc.output+"alongTrackDiags.dat");
		dw.writeData(dd,new Variable[]{sstm,vwsm,REFC,PEFC,ETA,zeta,fm,ISB,ULFI,mpi});	dw.closeFile();
	}
	
	
	static final class TCCase{
		//
		public String name=null;
		public String cond=null;
		public String btdata=null;
		public String eidata=null;
		public String range=null;
		public String output=null;
		public DataSets dset=null;
	}
}
