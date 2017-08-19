//
package pack;

import miniufo.application.basic.IndexInSC;
import miniufo.descriptor.DataDescriptor;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.diagnosis.Variable;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;


public final class ParamSensRadius{
	//
	final static String path="D:/Data/DiagnosisVortex/Haima/EFCIndex/";
	
	
	//
	public static void main(String[] args){
		DiagnosisFactory df=DiagnosisFactory.parseFile(path+"Haima.nc");
		DataDescriptor dd=df.getDataDescriptor();
		
		Variable[] idx=IndexInSC.c2DHorizontalIndex(dd,"lon(85,195);lat(0,60);time(2004.09.14.06,2004.09.14.06)",
		0.3f,25,72,9,18,"REFC","PEFC","AEFC","EAMA","FFCT","FFBS","ISB","ETA");
		
		for(Variable v:idx) v.setUndef(dd.getUndef(null));
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,path+"ParamSens300_600.dat");
		dw.writeData(dd,idx);	dw.closeFile();
	}
}
