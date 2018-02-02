//
package operational;

import miniufo.application.basic.DynamicMethodsInSC;
import miniufo.application.basic.SphericalHarmonicExpansion;
import miniufo.application.basic.VelocityFieldInSC;
import miniufo.descriptor.DataDescriptor;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.diagnosis.Range;
import miniufo.diagnosis.SphericalSpatialModel;
import miniufo.diagnosis.Variable;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;


//
public final class IndexVSDiv{
	//
	static final String path="D:/Data/ULFI/";
	
	
	//
	public static void main(String[] args){
		DiagnosisFactory df=DiagnosisFactory.parseFile(path+"JMA/2016/1612/1612.ctl");
		DataDescriptor dd=df.getDataDescriptor(); df.setPrinting(false);
		
		SphericalSpatialModel ssm=new SphericalSpatialModel(dd);
		VelocityFieldInSC vf=new VelocityFieldInSC(ssm);
		DynamicMethodsInSC dm=new DynamicMethodsInSC(ssm);
		SphericalHarmonicExpansion she=new SphericalHarmonicExpansion(ssm);
		
		she.setM(240);
		
		Range r=new Range("",dd);
		Variable[] wind=df.getVariables(r,"u","v");
		
		Variable vor=dm.c2DVorticity(wind[0],wind[1]);
		Variable sf=she.solvePoissonEquation(vor);
		Variable[] windR=vf.cRotationalVelocity(sf);
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,path+"JMA/2016/1612/1612rw.dat");
		dw.writeData(dd,wind[0],wind[1],windR[0],windR[1],vor,sf);	dw.closeFile();
	}
}
