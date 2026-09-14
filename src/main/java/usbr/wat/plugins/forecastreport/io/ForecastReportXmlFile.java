/*
 *
 *  * Copyright 2023 United States Bureau of Reclamation (USBR).
 *  * United States Department of the Interior
 *  * All Rights Reserved. USBR PROPRIETARY/CONFIDENTIAL.
 *  * Source may not be released without written approval
 *  * from USBR
 *
 */

package usbr.wat.plugins.forecastreport.io;

import java.util.Arrays;
import java.util.List;
import com.rma.util.XMLUtilities;
import org.jdom.Element;
import rma.util.RMAIO;
import usbr.wat.plugins.actionpanel.io.ReportXmlFile;
import usbr.wat.plugins.actionpanel.model.AbstractSimulationGroup;
import usbr.wat.plugins.actionpanel.model.SimulationReportInfo;
import usbr.wat.plugins.actionpanel.model.forecast.EnsembleReportInfo;
import usbr.wat.plugins.actionpanel.model.forecast.ForecastSimulationGroup;

public class ForecastReportXmlFile extends ReportXmlFile
{
	private static final String ENSEMBLE_SETS = "EnsembleSets";
	private static final String ENSEMBLE_SET = "EnsembleSet";
	private static final String OPERATIONS_NAMES = "OperationsName";
	private static final String MET_NAME = "MetName";
	private static final String TEMP_TARGET_NAME = "TempTargetName";
	private static final String NAME = "Name";
	private static final String COLLECTIONS_START = "CollectionsStart";
	private static final String COLLECTIONS_END = "CollectionsEnd";
	private static final String MEMBERS_TO_REPORT = "MembersToReport";
	private static final String FORECAST_TYPE = "Forecast";
	private List<EnsembleReportInfo> _ensembleInfos;
	private ForecastSimulationGroup _simulationGroup;

	public ForecastReportXmlFile(String filename)
	{
		super(filename);
	}

	protected void addAdditionalInfoForSim(Element simElem, SimulationReportInfo info)
	{
		Element ensemblesElem = new Element(ENSEMBLE_SETS);
		simElem.addContent(ensemblesElem);
		EnsembleReportInfo esInfo;
		for (int i = 0;i < _ensembleInfos.size(); i++ )
		{
			esInfo = _ensembleInfos.get(i);
			Element ensembleElem = new Element(ENSEMBLE_SET);
			ensemblesElem.addContent(ensembleElem);
			XMLUtilities.addChildContent(ensembleElem, NAME,esInfo.getEnsembleSet().getName());
			XMLUtilities.addChildContent(ensembleElem, OPERATIONS_NAMES,esInfo.getEnsembleSet().getBcData().getOpsDataName());
			XMLUtilities.addChildContent(ensembleElem, MET_NAME,esInfo.getEnsembleSet().getBcData().getMetDataName());
			XMLUtilities.addChildContent(ensembleElem, TEMP_TARGET_NAME,esInfo.getEnsembleSet().getTemperatureTargetSetName());
			int[] collectionIndexing = _simulationGroup.getEnsembleSetCollectionIndexing(info.getSimulation(), esInfo.getEnsembleSet());
			XMLUtilities.addChildContent(ensembleElem, COLLECTIONS_START,collectionIndexing[0]);
			XMLUtilities.addChildContent(ensembleElem, COLLECTIONS_END,collectionIndexing[1]);
			int[] members = esInfo.getMembersToReportOn();
			String membersStr = Arrays.toString(members);
			membersStr = RMAIO.removeChar(membersStr, '[');
			membersStr = RMAIO.removeChar(membersStr, ']');
			XMLUtilities.addChildContent(ensembleElem, MEMBERS_TO_REPORT,membersStr);
		}
	}

	public void setEnsembleInfo(List<EnsembleReportInfo> ensembleReportInfos)
	{
		_ensembleInfos = ensembleReportInfos;
	}

	public void setSimulationGroup(ForecastSimulationGroup simulationGroup)
	{
		_simulationGroup = simulationGroup;
	}

	protected String getReportType()
	{
		return FORECAST_TYPE;
	}
}
