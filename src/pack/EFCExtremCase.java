//
package pack;

import java.util.List;
import Package.IntensityModel;
import miniufo.application.advanced.CoordinateTransformation;
import miniufo.application.basic.DynamicMethodsInCC;
import miniufo.database.AccessBestTrack;
import miniufo.database.AccessBestTrack.DataSets;
import miniufo.descriptor.CsmDescriptor;
import miniufo.descriptor.CtlDescriptor;
import miniufo.diagnosis.CylindricalSpatialModel;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.diagnosis.Range;
import miniufo.diagnosis.SphericalSpatialModel;
import miniufo.diagnosis.Variable;
import miniufo.lagrangian.Typhoon;


//
public class EFCExtremCase{
	//
	private static final DataSets dsets=DataSets.JMA;
	
	private static final String tranges="time=1Jan2000-31Dec2004";
	
	//lu
	public static void main(String[] args){
		List<Typhoon> ls=AccessBestTrack.getTyphoons(IntensityModel.getPath(dsets),tranges,dsets);
		
		CtlDescriptor ctl=(CtlDescriptor)DiagnosisFactory.getDataDescriptor("D:/Data/ERAInterim/Data.ctl");
		
		for(Typhoon tr:ls){
			DiagnosisFactory df=DiagnosisFactory.parseContent(tr.toCSMString("D:/Data/ERAInterim/Data.ctl",36,25,2,0.3f,-650,850));
			CsmDescriptor dd=(CsmDescriptor)df.getDataDescriptor();
			
			dd.setCtlDescriptor(ctl);	df.setPrinting(false);
			CylindricalSpatialModel csm=new CylindricalSpatialModel(dd);
			DynamicMethodsInCC dm=new DynamicMethodsInCC(csm);
			CoordinateTransformation ct=new CoordinateTransformation(new SphericalSpatialModel(ctl),csm);
			
			Variable[] vars=df.getVariables(new Range("",dd),false,"u","v");
			Variable[] shrs=dm.cVerticalWindShear(vars[0],vars[1]);
			
			Variable shrsum=dm.cRadialAverage(shrs[0],1,15).anomalizeX();
			Variable shrsvm=dm.cRadialAverage(shrs[1],1,15).anomalizeX();
			
			Variable vwsm=shrsum.hypotenuse(shrsvm);
			
			Variable[] utvr=ct.reprojectToCylindrical(vars[0],vars[1]);
			Variable Va=utvr[1].copy(); Va.anomalizeX();
			
			dm.cStormRelativeAziRadVelocity(tr.getZonalVelocity(),tr.getMeridionalVelocity(),utvr[0],utvr[1]);
			
			Variable utm=utvr[0].anomalizeX();	utvr[1].anomalizeX();
			Variable refc=dm.cRadialAverage(dm.cREFC(utvr[0],utvr[1]), 9,18);	// 300-600 km
			Variable etam=dm.cRadialAverage(dm.cMeanAbsoluteVorticity(utm),9,18);
			Variable pefc=dm.cRadialAverage(dm.cPEFC(Va),9,18);
			
			float[] wind=tr.getWinds();
			float[] pres=tr.getPressures();
			float[] REFC= refc.getData()[1][0][0];
			float[] PEFC= pefc.getData()[1][0][0];
			float[] VWSM= vwsm.getData()[0][0][0];
			float[] Etam= etam.getData()[1][0][0];
			float[] ULFI=cULFI(REFC,PEFC,Etam);
			
			boolean flag=false;
			for(int l=0;l<tr.getTCount();l++)
			if(VWSM[l]<15&&ULFI[l]>4){
				flag=true;
				System.out.println(String.format(
					"%10s %s   Wind:%7.4f   Pres:%6.1f   REFC:%6.2f   PEFC:%6.2f   Etam:%7.3f   ULFI:%5.2f   VWS:%6.2f",
					tr.getName(),tr.getRecord(l),
					wind[l],pres[l],REFC[l],PEFC[l],
					Etam[l]*1e5f,ULFI[l],VWSM[l]
				));
			}
			if(flag) System.out.println();
		}
	}
	
	static float[] cULFI(float[] REFC,float[] PEFC,float[] Eta){
		int len=REFC.length;
		
		float[] re=new float[len];
		
		for(int l=0;l<len;l++) re[l]=(REFC[l]+PEFC[l])/(Eta[l]*86400f);
		
		return re;
	}
}
