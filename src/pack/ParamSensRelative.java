//
package pack;

import miniufo.application.basic.IndexInSC;
import miniufo.descriptor.DataDescriptor;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.diagnosis.Variable;
import miniufo.io.DataIOFactory;
import miniufo.io.DataWrite;


public final class ParamSensRelative{
	//
	final static String path="D:/Data/DiagnosisVortex/Haima/EFCIndex/";
	
	private enum Directions{ Fix,North,East,South,West}
	
	
	//
	public static void main(String[] args){
		for(Directions dir:Directions.values()) compute(dir,10);	// 10 m/s
		for(Directions dir:Directions.values()) compute(dir,5);		// 5  m/s
	}
	
	static void compute(Directions dir,float speed){
		float cu=0,cv=0;
		
		switch(dir){
		case Fix: break;
		case North: cv= speed; break;
		case East : cu= speed; break;
		case South: cv=-speed; break;
		case West : cu=-speed; break;
		default: throw new IllegalArgumentException("not supported direction: "+dir);
		}
		
		DiagnosisFactory df=DiagnosisFactory.parseFile(path+"Haima.nc");
		DataDescriptor dd=df.getDataDescriptor();
		
		Variable[] idx=IndexInSC.c2DHorizontalIndex(dd,"lon(85,195);lat(0,60);time(2004.09.14.06,2004.09.14.06)",
		cu,cv,0.3f,25,72,9,18,"REFC","PEFC","AEFC","EAMA","FFCT","FFBS");
		
		for(Variable v:idx) v.setUndef(dd.getUndef(null));
		
		DataWrite dw=DataIOFactory.getDataWrite(dd,path+"ParamSens"+dir+speed+".dat");
		dw.writeData(dd,idx);	dw.closeFile();
	}
}
