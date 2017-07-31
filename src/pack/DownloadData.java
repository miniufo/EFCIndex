//
package pack;

import org.ecmwf.DataServer;
import org.json.JSONObject;


//
public final class DownloadData{
	//
	public static void main(String[] args){
		//downloadPL("1985-08-01/to/1985-09-30","d:/ElenaWnd.nc");
		//downloadSST("1985-08-01/to/1985-09-30","d:/ElenaSST.nc");
		
		//downloadPL("2004-09-01/to/2004-09-30","d:/HaimaWnd.nc");
		//downloadSST("2004-09-01/to/2004-09-30","d:/HaimaSST.nc");
		
		//downloadPL("1992-08-15/to/1992-09-20","d:/OmarWnd.nc");
		//downloadSST("1992-08-15/to/1992-09-20","d:/OmarSST.nc");
		
		//downloadPL("2000-07-01/to/2000-07-15","d:/KirogiWnd.nc");
		//downloadSST("2000-07-01/to/2000-07-15","d:/KirogiSST.nc");
		
		downloadPL("2001-07-20/to/2001-08-05","d:/Kong-ReyWnd.nc");
		downloadSST("2001-07-20/to/2001-08-05","d:/Kong-ReySST.nc");
	}
	
	static void downloadPL(String date,String name){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		request.put("dataset" , "interim");
		request.put("type"    , "an");	// type of field (an, fc) for analysis, forecast
		request.put("class"   , "ei");	// ECMWF classification (od, rd, ei, e4, er) for operational department, research department
		//request.put("expver"  , "1");	// experiment version
		//request.put("domain"  , "g");	// (g) for global
		//request.put("accuracy", "16");	// number of bits per data value in GRIB (16)
		//request.put("number  ", "1");	// ensemble member
		request.put("date"    , date);
		request.put("stream"  , "oper");// (oper, enfo, da, mnth, moda, mdfa, wave) for operational, ensemble forecast, daily, synoptic monthly, monthly mean of daily mean
		request.put("repres"  , "ll");	// (sh, ll, gg) for spherical harmonics, lat/lon, Gaussian grid
		request.put("levtype" , "pl");	// (ml, sfc, pl, pt, pv) for model level, surface, pressure level, potential temperature, potential vorticity
		request.put("levelist", "850/200");	// (all, off) off if levtype = sfc
		request.put("param"   , "u/v");	//
		request.put("step"    , "0");	// 24/to/240/by/24
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		//request.put("area"    , "84/81/-15/222");
		//request.put("resol"   , "av");	// triangular trunction (319, auto, av)
		request.put("grid"    , "0.75/0.75");
		request.put("format"  , "netcdf");
		request.put("target"  , name);
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
	
	static void downloadSST(String date,String name){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		request.put("dataset" , "interim");	// interim, era5, era40
		request.put("type"    , "an");	// type of field (an, fc, 4v) for analysis, forecast, 4Dvar
		request.put("class"   , "ei");	// ECMWF classification (od, rd, ei, e4, er, ea) for operational department, research department
		//request.put("expver"  , "1");	// experiment version
		//request.put("domain"  , "g");	// (g) for global
		//request.put("accuracy", "16");	// number of bits per data value in GRIB (16)
		//request.put("number  ", "1");	// ensemble member
		request.put("date"    , date);
		request.put("stream"  , "oper");// (oper, enfo, da, mnth, moda, mdfa, wave) for operational, ensemble forecast, daily, synoptic monthly, monthly mean of daily mean
		request.put("repres"  , "ll");	// (sh, ll, gg) for spherical harmonics, lat/lon, Gaussian grid
		request.put("levtype" , "sfc");	// (ml, sfc, pl, pt, pv) for model level, surface, pressure level, potential temperature, potential vorticity
		//request.put("levelist", "850/200");	// (all, off) off if levtype = sfc
		request.put("param"   , "sst");	//
		request.put("step"    , "0");	// 24/to/240/by/24
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		//request.put("area"    , "84/81/-15/222");
		//request.put("resol"   , "av");	// triangular trunction (319, auto, av)
		request.put("grid"    , "0.75/0.75");
		request.put("format"  , "netcdf");
		request.put("target"  , name);
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
	
	static void downloadExample1(){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		// request example - Interim Re-Analysis
		request.put("class"   , "ei");	// (ei, od) for ERAinterim, operational analysis
		request.put("stream"  , "oper");// (oper, enfo, da) for operational, ensemble forecast
		request.put("expver"  , "1");	// experiment version
		request.put("date"    , "1985-08-01/to/1985-09-30");
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		request.put("type"    , "an");	// (an, fc) for analysis, forecast
		request.put("levtype" , "sfc");	// (ml, sfc, pl) for model level, surface, pressure level
		request.put("param"   , "u/v");	//
		request.put("target"  , "d:/Elena.nc");
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
	
	static void downloadExample2(){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		// request example - Ensemble forecast, retrieval of 20 first members of the EPS
		request.put("class"   , "od");	// (ei, od) for ERAinterim, operational analysis
		request.put("stream"  , "enfo");// (oper, enfo, da) for operational, ensemble forecast
		request.put("expver"  , "1");	// experiment version
		request.put("date"    , "1985-08-01/to/1985-09-30");
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		request.put("step"    , "12/36/60");
		request.put("type"    , "pf");	// (an, fc, pf) for analysis, forecast
		request.put("levtype" , "sfc");	// (ml, sfc, pl) for model level, surface, pressure level
		request.put("param"   , "u/v");	//
		request.put("number"  , "1/to/20");
		request.put("target"  , "d:/Elena.nc");
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
	
	static void downloadExample3(){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		// request example - Operational analysis
		request.put("class"   , "od");	// (od, rd, e4, ei) for  operational analysis
		request.put("stream"  , "oper");// (oper, enfo, da) for operational, ensemble forecast
		request.put("expver"  , "1");	// experiment version
		request.put("date"    , "1985-08-01/to/1985-09-30");
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		request.put("step"    , "12/36/60");
		request.put("type"    , "an");	// type of field (an, fc, pf) for analysis, forecast
		request.put("levtype" , "sfc");	// (ml, sfc, pl) for model level, surface, pressure level
		request.put("param"   , "u/v");	//
		request.put("number"  , "1/to/20");
		request.put("target"  , "d:/Elena.nc");
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
	
	static void downloadExample4(){
		DataServer server = new DataServer();
		
		JSONObject  request = new JSONObject();
		
		// request example - ODB observation feedback from conventional data
		request.put("class"   , "od");	// (ei, od) for ERAinterim, operational analysis
		request.put("stream"  , "oper");// (oper, enfo, da) for operational, ensemble forecast
		request.put("expver"  , "1");	// experiment version
		request.put("date"    , "1985-08-01/to/1985-09-30");
		request.put("time"    , "00:00:00/06:00:00/12:00:00/18:00:00");
		request.put("step"    , "12/36/60");
		request.put("type"    , "ofb");	// type of field (an, fc, ofb, pf) for analysis, forecast, observational feedback
		request.put("obsgroup", "conv");	// (ml, sfc, pl) for model level, surface, pressure level
		request.put("filter"  , "select lat,lon,obsvalue where varno=39");
		request.put("target"  , "d:/Elena.nc");
		
		try{
			server.retrieve(request);
			
		}catch(Exception e){ e.printStackTrace(); System.exit(0);}
	}
}
