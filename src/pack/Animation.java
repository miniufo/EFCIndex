//
package pack;

import java.io.File;
import java.io.FileWriter;

import pack.EFCIndex.TCCase;
import miniufo.basic.InterpolationModel.Type;
import miniufo.database.AccessBestTrack;
import miniufo.descriptor.DataDescriptor;
import miniufo.diagnosis.DiagnosisFactory;
import miniufo.lagrangian.Typhoon;
import miniufo.util.DataInterpolation;


//
public class Animation{
	//
	private static final int interpMultiple=6;
	
	private static final TCCase tc=EFCIndex.Elena;
	
	//
	public static void main(String[] args){
		Typhoon tr=AccessBestTrack.getTyphoons(tc.btdata,tc.cond,tc.dset).get(0);
		System.out.println(tr);
		
		//interpData(tc.output+"index.ctl"    ,tc.output+"Animation/indexInterp.dat"    );
		//interpData(tc.output+"intensity.ctl",tc.output+"Animation/intensityInterp.dat");
		
		writeGS(tc.output+"Animation/",tr,DiagnosisFactory.getDataDescriptor(tc.output+"Animation/indexInterp.ctl"));
	}
	
	static void interpData(String fin,String fout){
		DataDescriptor dd=DiagnosisFactory.getDataDescriptor(fin);
		
		DataInterpolation di=new DataInterpolation(dd);
		
		di.temporalInterp(fout,Type.LINEAR,(dd.getTCount()-1)*interpMultiple+1);
	}
	
	static void writeGS(String path,Typhoon tro,DataDescriptor dd){
		Typhoon tr=tro.interpolateAlongT(interpMultiple-1);
		
		StringBuffer sb=new StringBuffer();
		
		sb.append("'open "+path+"indexInterp.ctl'\n");
		sb.append("'open "+path+"intensityInterp.ctl'\n\n");
		
		sb.append("lons=\""); for(int l=0;l<tr.getTCount();l++) sb.append(tr.getXPositions()[l]+" "); sb.append("\"\n");
		sb.append("lats=\""); for(int l=0;l<tr.getTCount();l++) sb.append(tr.getYPositions()[l]+" "); sb.append("\"\n");
		sb.append("tend=65\n\n");
		
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
		sb.append("'set lon 90 150'\n");
		sb.append("'set lev 200'\n");
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
		sb.append("'d prs"+tr.getName()+".2'\n");
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
		sb.append("'d prs"+tr.getName()+".2'\n");
		sb.append("'q w2xy 'tim' 'prs\n");
		sb.append("xx=subwrd(result,3)\n");
		sb.append("yy=subwrd(result,6)\n");
		sb.append("'draw mark 3 'xx' 'yy' 0.3'\n");
		sb.append("'draw title minimum central pressure'\n");
		sb.append("ttt=tt+100\n");
		sb.append("'printim "+path+"animation'ttt'.png white x1600 y1200'\n");
		sb.append("'c'\n");
		sb.append("cc=cc+1\n");
		sb.append("tt=tt+1\n");
		sb.append("endwhile\n\n");
		
		sb.append("'disable print'\n");
		sb.append("'close 2'\n");
		sb.append("'close 1'\n");
		sb.append("'reinit'\n\n");
		
		sb.append("'!convert -delay 8 -crop 1400x735+50+20 +repage "+path+"animation*.png "+path+"animation.gif'\n");
		sb.append("'!rm "+path+"animation*.png'\n");
		
		try{
			FileWriter fw=new FileWriter(new File(path+"animation.gs"));
			fw.write(sb.toString());	fw.close();
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
}
