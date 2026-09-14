/*
 * Copyright 2021  Hydrologic Engineering Center (HEC).
 * United States Army Corps of Engineers
 * All Rights Reserved.  HEC PROPRIETARY/CONFIDENTIAL.
 * Source may not be released without written approval
 * from HEC
 */
package usbr.wat.plugins.forecastreport.actions;


import java.awt.EventQueue;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.Action;
import hec2.plugin.model.Simulation;
import org.python.core.PyCode;
import org.python.util.PythonInterpreter;

import com.rma.io.FileManagerImpl;
import com.rma.io.RmaFile;
import com.rma.model.Project;

import hec2.plugin.model.ModelAlternative;
import hec2.wat.model.WatSimulation;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.repo.FileRepositoryPersistenceServiceFactory;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.PersistenceServiceFactory;
import net.sf.jasperreports.repo.RepositoryService;
import rma.util.RMAIO;
import usbr.wat.plugins.actionpanel.ActionPanelPlugin;
import usbr.wat.plugins.actionpanel.ActionsWindow;
import usbr.wat.plugins.actionpanel.actions.AbstractReportAction;
import usbr.wat.plugins.actionpanel.io.ReportOptions;
import usbr.wat.plugins.actionpanel.model.forecast.ForecastReportingPlugin;
import usbr.wat.plugins.actionpanel.model.ReportsManager;
import usbr.wat.plugins.actionpanel.model.SimulationReportInfo;
import usbr.wat.plugins.actionpanel.ui.UsbrPanel;
import usbr.wat.plugins.forecastreport.io.ForecastReportXmlFile;
import usbr.wat.plugins.actionpanel.model.forecast.EnsembleReportInfo;

/**
 * @author Mark Ackerman
 *
 */
@SuppressWarnings("serial")
public class CreateReportsAction extends AbstractReportAction
	implements ForecastReportingPlugin
	
{
	
	private static final String JASPER_REPORT_DIR = "Reports";
	public static final String JASPER_OUT_FILE = "WTMP_report_draft-";
	public static final String REPORT_FILE_EXT = ".pdf";
	private static final String XML_DATA_DOCUMENT = "USBRAutomatedReportDataAdapter.xml";
	
	
	private static final String SCRIPTS_DIR = "scripts";
	private static final String MAVEN_PATH = "usbr.wat.plugins/usbr-forecast-report";
	
	private ActionsWindow _parent;
	
	private PythonInterpreter  _interp;
	private PyCode _pycode;
	
	public CreateReportsAction(ActionsWindow parent )
	{
		super("Create Reports", null);
		setEnabled(false);
		_parent = parent;
	}
	
	public boolean createReport(List<SimulationReportInfo> sims, List<EnsembleReportInfo> ensembleReportInfos, ReportOptions options)
	{
		
		WatSimulation sim;
		long t1 = System.currentTimeMillis();
		String xmlFile = createSimulationXmlFile(sims, ensembleReportInfos);
		if ( xmlFile != null )
		{
			if ( !editDataAdapterFile(sims.get(0).getSimFolder()))
			{
				return false;
			}
			if ( runPythonScript(xmlFile))
			{
				return runJasperReport(sims.get(0), options);
			}
		}
		return false;
	}
	
	/**
	 * @param sims
	 * @param ensembleReportInfos
	 */
	private String createSimulationXmlFile(List<SimulationReportInfo> sims, List<EnsembleReportInfo> ensembleReportInfos)
	{
		_parent = ActionPanelPlugin.getInstance().getActionsWindow();
		Project prj = Project.getCurrentProject();
		String studyDir = prj.getProjectDirectory();
		String simFolder = sims.get(0).getSimFolder();
		String filename = RMAIO.concatPath(simFolder, REPORT_DIR);
		filename = RMAIO.concatPath(filename, RMAIO.userNameToFileName(sims.get(0).getName()+"forecast")+".xml");
		if ( Boolean.getBoolean("SkipForecastReportFile"))
		{
			return filename;
		}
		
		ForecastReportXmlFile xmlFile = new ForecastReportXmlFile(filename);
		xmlFile.setStudyInfo(studyDir, getObsDataPath(studyDir));
		List<SimulationReportInfo>simList = new ArrayList<>();
		simList.addAll(sims);
		xmlFile.setSimulationInfo(_parent.getForecastPanel().getSimulationGroup().getName(), sims);
		xmlFile.setSimulationGroup(_parent.getForecastPanel().getSimulationGroup());
		xmlFile.setEnsembleInfo(ensembleReportInfos);
		//xmlFile.setEnsembleSetInfo();
		if (  xmlFile.createXMLFile() )
		{
			return filename;
		}
		return null;
	}
	
	
	
	/**
	 * run the bat file to create the XMl file that's input to the jasper report
	 * @param sim
	 * @return
	 */
	public boolean runPythonScript(WatSimulation sim, ModelAlternative modelAlt, String baseSimulationName)
	{
		
		
		// first run the python script through the .bat file
		// bat file needs: 
		// 1. watershed path
		// 2. simulation path
		// 3. model name ... i.e. ResSim
		// 4. alternative's F-Part
		// 5. folder to the observation data in the study
		// 6. alternative's name
		// 7. simulation's base name
		
		long t1 = System.currentTimeMillis();
		try
		{
			String fpart = findFpartForPython(sim, modelAlt);
			if ( fpart == null )
			{
				System.out.println("createReportAction:no ResSim Alternative found in Simulation "+sim);
				return false;
			}

			List<String>cmdList = new ArrayList<>();
			String dirToUse = getDirectoryToUse();
			String exeFile = RMAIO.concatPath(dirToUse, PYTHON_REPORT_BAT);
			String studyDir = Project.getCurrentProject().getProjectDirectory();
			//cmdList.add("cmd.exe");
			//cmdList.add("/c");
			cmdList.add(exeFile);
			cmdList.add(studyDir);
			// hack for having a comma in the path and the RMAIO.userNameToFileName() not catching it
			String simDir = sim.getSimulationDirectory();
			simDir = RMAIO.removeChar(simDir, ',');
			cmdList.add(simDir);
			cmdList.add(modelAlt.getProgram());
			cmdList.add(fpart);
			String obsPath = getObsDataPath(studyDir);
			cmdList.add(obsPath);
			cmdList.add(modelAlt.getName());
			cmdList.add(baseSimulationName);



			return runProcess(cmdList, dirToUse);
		}
		finally
		{
			long t2 = System.currentTimeMillis();
			System.out.println("runProcess:time to run python for "+sim+" alt "+modelAlt+" is "+(t2-t1)+"ms");
		}
		
	}
	
	
	
	
	
	/**
	 * @param sim
	 */
	public boolean runJasperReport(SimulationReportInfo sim, ReportOptions options)
	{
		long t1 = System.currentTimeMillis();
		try
		{
			//Log log = LogFactory.getLog(JasperFillManager.class);
			String studyDir = Project.getCurrentProject().getProjectDirectory();
			String simDir = sim.getSimFolder();
			String jasperReportFolder = getJasperRelativeFolder();
			String jasperRepoDir = RMAIO.concatPath(studyDir, REPORT_DIR);
			String rptFile = RMAIO.concatPath(jasperRepoDir, JASPER_FILE);
			String installDir = System.getProperty("user.dir");
			installDir = RMAIO.getDirectoryFromPath(installDir);
			//rptFile = RMAIO.concatPath(rptFile, JASPER_FILE);


			System.out.println("runReportWithOutputFile:report repository:"+jasperRepoDir);

			SimpleJasperReportsContext context = new SimpleJasperReportsContext();
			FileRepositoryService fileRepository = new FileRepositoryService(context, 
					jasperRepoDir, true);
			context.setExtensions(RepositoryService.class, Collections.singletonList(fileRepository));
			context.setExtensions(PersistenceServiceFactory.class, 
					Collections.singletonList(FileRepositoryPersistenceServiceFactory.getInstance()));
			String inJasperFile = rptFile;

			JRPropertiesUtil.getInstance(context).setProperty("net.sf.jasperreports.xpath.executer.factory",
					"net.sf.jasperreports.engine.util.xml.JaxenXPathExecuterFactory");

			String outputFile = RMAIO.concatPath(simDir, REPORT_DIR);
			RmaFile simDirFile = FileManagerImpl.getFileManager().getFile(outputFile);
			if ( !simDirFile.exists() )
			{
				if ( !simDirFile.mkdirs())
				{
					System.out.println("runJasperReport:failed to create folder "+simDirFile.getAbsolutePath());
				}
			}

			if ( !compileJasperFiles(studyDir, installDir, jasperReportFolder))
			{
				return false;
			}

			JasperPrint jasperPrint = fillReport(context, studyDir, installDir, jasperReportFolder, sim, options);
			if ( jasperPrint == null )
			{
				return false;
			}	
			
			long t4 = System.currentTimeMillis();
			outputFile = RMAIO.concatPath(outputFile, JASPER_OUT_FILE);

			SimpleDateFormat fmt= new SimpleDateFormat("yyyy.MM.dd-HHmm");
			
			Date date = new Date();
			outputFile = outputFile.concat(fmt.format(date));
			outputFile = outputFile.concat(REPORT_FILE_EXT);
			

			// fills compiled report with parameters and a connection
			JRExporter exporter = options.getOutputType().buildExporter(jasperPrint, outputFile);

			try
			{
				exporter.exportReport();
				System.out.println("runJasperReport:comparison report written to "+outputFile);
			}
			catch (JRException e)
			{
				e.printStackTrace();
				return false;
			}

			long t5 = System.currentTimeMillis();
			System.out.println("runJasperReport:time to write jasper comparison report for "+sim+ "is "+(t5-t4)+"ms");
			return true;
		}
		finally
		{
			long end = System.currentTimeMillis();
			System.out.println("runJasperReport:total time to create jasper comparison report for "+sim+" is "+(end-t1)+"ms");
		}
	}


	@Override
	public boolean createReport(List<SimulationReportInfo> sris, ReportOptions options)
	{
		return false;
	}

	@Override
	public String getName()
	{
		return "Forecast Report";
	}
	@Override
	public String getDescription()
	{
		return "Forecast Report for Forecast Simulations";
	}
	@Override
	public boolean isComparisonReport()
	{
		return false;
	}
	@Override
	public boolean isIterationReport()
	{
		return false;
	}
	
	@Override
	public String toString()
	{
		return getName();
	}
	public static void register()
	{
		ReportsManager.register(new CreateReportsAction(ActionPanelPlugin.getInstance().getActionsWindow()));
	}
	
	public static void main(String[] args)
	{
		EventQueue.invokeLater(()->register());
	}

	@Override
	public String getMavenPath()
	{
		return MAVEN_PATH;
	}

	@Override
	public Action getReportAction(ActionsWindow parent, UsbrPanel parentPanel)
	{
		return new DisplayEnsembleSelectorAction(parent, parentPanel);
	}

	@Override
	public boolean createReport(SimulationReportInfo sims, List<EnsembleReportInfo> ensembleReportInfos, ReportOptions options)
	{
		List<SimulationReportInfo>simInfos = new ArrayList<>();
		simInfos.add(sims);
		return createReport(simInfos, ensembleReportInfos, options);
	}
}
