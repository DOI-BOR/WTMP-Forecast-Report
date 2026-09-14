/*
 *
 *  * Copyright 2023 United States Bureau of Reclamation (USBR).
 *  * United States Department of the Interior
 *  * All Rights Reserved. USBR PROPRIETARY/CONFIDENTIAL.
 *  * Source may not be released without written approval
 *  * from USBR
 *
 */

package usbr.wat.plugins.forecastreport.editors;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import com.rma.io.FileManagerImpl;
import com.rma.model.Project;
import hec.util.AnimatedWaitGlassPane;
import hec2.wat.model.WatSimulation;
import rma.swing.ButtonCmdPanel;
import rma.swing.ButtonCmdPanelListener;
import rma.swing.RmaInsets;
import rma.swing.RmaJComboBox;
import rma.swing.RmaJDialog;
import rma.swing.RmaJTable;
import rma.swing.RmaJTextField;
import rma.swing.list.RmaListModel;
import rma.util.IntVector;
import rma.util.RMAFilenameFilter;
import rma.util.RMAIO;
import usbr.wat.plugins.actionpanel.ActionsWindow;
import usbr.wat.plugins.actionpanel.actions.DisplayReportAction;
import usbr.wat.plugins.actionpanel.actions.forecast.RunForecastSimulationAction;
import usbr.wat.plugins.actionpanel.editors.ReportOptionsPanel;
import usbr.wat.plugins.actionpanel.editors.TemplateWrapper;
import usbr.wat.plugins.actionpanel.io.ReportOptions;
import usbr.wat.plugins.actionpanel.model.forecast.ForecastReportingPlugin;
import usbr.wat.plugins.actionpanel.model.ReportPlugin;
import usbr.wat.plugins.actionpanel.model.ReportsManager;
import usbr.wat.plugins.actionpanel.model.SimulationReportInfo;
import usbr.wat.plugins.actionpanel.model.forecast.EnsembleReportInfo;
import usbr.wat.plugins.actionpanel.model.forecast.EnsembleSet;
import usbr.wat.plugins.actionpanel.model.forecast.ForecastSimulationGroup;
import usbr.wat.plugins.actionpanel.ui.UsbrPanel;


import static rma.swing.ButtonCmdPanel.CLOSE_BUTTON;
import static rma.swing.ButtonCmdPanel.OK_BUTTON;
import static usbr.wat.plugins.actionpanel.editors.forecast.DisplayReportsSelector.REPORTS_DIRS;

public class DisplayForecastReportsSelector extends RmaJDialog
{
	private static final int SELECTION_COL = 0;
	private static final int ENSEMBLE_SET_COL = 1;
	private static final int MEMBERS_TO_REPORT_COL = 2;
	private final ActionsWindow _parent;
	private final UsbrPanel _parentPanel;
	private RmaJTable _ensembleTable;
	private ButtonCmdPanel _cmdPanel;
	private JButton _selectAllBtn;
	private JButton _unselectAllBtn;
	private ReportOptionsPanel _optionsPanel;
	private RmaJTextField _simulationFld;
	private List<SimulationReportInfo> _sims;
	private AnimatedWaitGlassPane _agp;
	private Component _glassPane;
	private RmaJComboBox _reportTypeCombo;

	public DisplayForecastReportsSelector(ActionsWindow parent, UsbrPanel parentPanel)
	{
		super(parent, true);
		_parent =parent;
		_parentPanel = parentPanel;
		buildControls();
		addListeners();
		pack();
		setSize(600,400);
		setLocationRelativeTo(getParent());
	}

	private void buildControls()
	{
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		getContentPane().setLayout(new GridBagLayout());
		setTitle("Select Ensemble Sets to Report on");

		JLabel label = new JLabel("Simulation:");
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = 1;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS5505;
		add(label, gbc);

		_simulationFld = new RmaJTextField();
		_simulationFld.setEditable(false);
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = RmaInsets.INSETS5505;
		add(_simulationFld, gbc);

		String[] headers = new String[]{"Select", "Ensemble Set",  "Members for Report ", "Computed Members"};
		_ensembleTable = new RmaJTable(this, headers)
		{
			public boolean isCellEditable(int row, int column)
			{
				return column == 0 || column == 2;
			}

		};
		_ensembleTable.setCheckBoxCellEditor(0);
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = RmaInsets.INSETS5505;
		add(_ensembleTable.getScrollPane(), gbc);

		JPanel buttonPanel = new JPanel(new GridBagLayout());
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS5505;
		add(buttonPanel, gbc);

		_selectAllBtn = new JButton("Select All");
		_selectAllBtn.setToolTipText("Select All the Ensemble Members for the selected table rows");
		_selectAllBtn.setEnabled(false);
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = 1;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS5505;
		buttonPanel.add(_selectAllBtn, gbc);

		_unselectAllBtn = new JButton("Clear All");
		_selectAllBtn.setToolTipText("Clear All the Ensemble Members for the selected table rows");
		_unselectAllBtn.setEnabled(false);
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS5505;
		buttonPanel.add(_unselectAllBtn, gbc);

		_optionsPanel = new ReportOptionsPanel();
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = 1;
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS5505;
		add(_optionsPanel, gbc);


		label = new JLabel("Report Type:");
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = 1;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = RmaInsets.INSETS0505;
		add(label, gbc);

		_reportTypeCombo = new RmaJComboBox<>();
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = RmaInsets.INSETS0505;
		add(_reportTypeCombo, gbc);

		_cmdPanel = new ButtonCmdPanel(ButtonCmdPanel.OK_BUTTON| CLOSE_BUTTON);
		JButton okBtn = _cmdPanel.getButton(OK_BUTTON);
		okBtn.setText("Create Report");
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = RmaInsets.INSETS5555;
		add(_cmdPanel, gbc);
	}

	private void addListeners()
	{
		_ensembleTable.getSelectionModel().addListSelectionListener(e->tableRowSelected(e));
		_selectAllBtn.addActionListener(e->selectAllAction());
		_unselectAllBtn.addActionListener(e->unselectAllAction());
		_cmdPanel.addCmdPanelListener(new ButtonCmdPanelListener()
		{
			public void buttonCmdActionPerformed(ActionEvent e)
			{
				switch (e.getID())
				{
					case OK_BUTTON:
						createReport();
						break;
					case CLOSE_BUTTON:
						setVisible(false);
						break;
				}
			}
		});
	}

	private void tableRowSelected(ListSelectionEvent e)
	{
		if ( e.getValueIsAdjusting())
		{
			return;
		}
		int row = _ensembleTable.getSelectedRow();
		boolean enabled = row > -1;

		_selectAllBtn.setEnabled(enabled);
		_unselectAllBtn.setEnabled(enabled);
	}

	private void unselectAllAction()
	{
		int[] rows = _ensembleTable.getSelectedRows();
		if ( rows == null || rows.length == 0 )
		{
			return;
		}
		for (int r = 0;r < rows.length;r++)
		{
			_ensembleTable.setValueAt("", r, 2);
		}
	}

	private void selectAllAction()
	{
		int[] rows = _ensembleTable.getSelectedRows();
		if ( rows == null || rows.length == 0 )
		{
			return;
		}
		for (int r = 0;r < rows.length;r++)
		{
			Object obj = _ensembleTable.getValueAt(r, 3);
			_ensembleTable.setValueAt(obj, r, 2);
		}
	}

	public void setVisible(boolean visible)
	{
		if ( visible )
		{
			if ( !checkSims())
			{
				return;
			}
		}
		super.setVisible(visible);
		if ( !visible)
		{
			//saveSelectedReports();
		}
	}
	private boolean checkSims()
	{
		if ( _parent.getSimulationGroup() == null )
		{
			JOptionPane.showMessageDialog(_parent,"Please create or select a Simulation Group first",
					"No Simulation Group Selected", JOptionPane.INFORMATION_MESSAGE);
			return false;

		}

		List<SimulationReportInfo> sims = _parentPanel.getSimulationReportInfos();

		SimulationReportInfo sri;
		String folder;

		Iterator<SimulationReportInfo> iter = sims.iterator();
		while ( iter.hasNext())
		{
			sri = iter.next();
			folder = sri.getSimFolder();
			if ( !FileManagerImpl.getFileManager().fileExists(folder))
			{
				final SimulationReportInfo fSri = sri;
				EventQueue.invokeLater(()->JOptionPane.showMessageDialog(_parent, "Simulation "+fSri.getSimulation().getName()
						+" has no results so no report can be created for it.","No Results", JOptionPane.INFORMATION_MESSAGE));
				iter.remove();
			}
		}

		if ( sims.isEmpty())
		{
			EventQueue.invokeLater(()->JOptionPane.showMessageDialog(_parent,"There are no Simulations with results selected to create reports for",
					"No Simulations Selected", JOptionPane.INFORMATION_MESSAGE));
			return false;
		}
		fillForm(sims);
		return true;
	}
	private void fillForm(List<SimulationReportInfo> sims)
	{
		_ensembleTable.deleteCells();
		boolean canBeComparisionReport = sims.size()>1;
		ReportPlugin plugin;
		ForecastSimulationGroup simGroup = (ForecastSimulationGroup) _parent.getSimulationGroup();
		Vector<Object> row;
		_sims = sims;
		for(int s = 0;s < sims.size();s++ )
		{
			SimulationReportInfo sim = sims.get(s);
			WatSimulation simulation = sim.getSimulation();
			_simulationFld.setText(simulation.getName());
			List<EnsembleSet> esets = simGroup.getEnsembleSets(simulation);
			for (int e = 0;e < esets.size(); e++ )
			{
				row = new Vector<>();
				row.add(Boolean.FALSE);
				EnsembleSet eset = esets.get(e);
				row.add(eset);
				row.add("");
				IntVector members = eset.getComputedMembers();
				int[] membersArray = members.toArray();
				Arrays.sort(membersArray);
				String str = Arrays.toString(membersArray);
				str = RMAIO.removeChar(str, ']');
				str = RMAIO.removeChar(str, '[');
				row.add(str);

				_ensembleTable.appendRow(row);

			}
		}
		fillReportTemplateCombo(getForecastReportingPlugin());
		//updateCreateReportButtonState();
	}
	public ForecastReportingPlugin getForecastReportingPlugin()
	{
		List<ReportPlugin> plugins = ReportsManager.getPlugins();
		for (int i = 0;i < plugins.size(); i++ )
		{
			if ( plugins.get(i) instanceof ForecastReportingPlugin )
			{
				return (ForecastReportingPlugin) plugins.get(i);
			}
		}
		return null;
	}
	private List<EnsembleReportInfo> getEnsembleInfos()
	{
		int rows = _ensembleTable.getNumRows();
		Object obj;
		List<EnsembleReportInfo>eInfoList = new ArrayList<>();
		for (int r = 0;r < rows; r++ )
		{
			obj = _ensembleTable.getValueAt(r, SELECTION_COL);
			if ( isSelected(obj))
			{
				int [] memberSet = getMembers((String)_ensembleTable.getValueAt(r, MEMBERS_TO_REPORT_COL));
				if ( memberSet != null && memberSet.length > 0 )
				{
					EnsembleReportInfo eInfo = new EnsembleReportInfo((EnsembleSet)_ensembleTable.getValueAt(r,ENSEMBLE_SET_COL),memberSet);
					eInfoList.add(eInfo);
				}
			}
		}
		return eInfoList;
	}

	private int[] getMembers(String memberSet)
	{
		return RunForecastSimulationAction.getIntegerSet(memberSet);
	}

	private boolean isSelected(Object obj)
	{
		return  obj != null && (obj == Boolean.TRUE || RMAIO.parseBoolean(obj.toString(), false));
	}

	private boolean createReport()
	{
		_ensembleTable.commitEdit(true);

		List<ReportPlugin> plugins = ReportsManager.getPlugins();
		ForecastReportingPlugin plugin = null;
		for (int i = 0;i < plugins.size(); i++ )
		{
			if ( plugins.get(i) instanceof ForecastReportingPlugin )
			{
				plugin = (ForecastReportingPlugin) plugins.get(i);
				break;
			}
		}
		if ( plugin == null )
		{
			JOptionPane.showMessageDialog(this, "No Forecast Reporting Plugin found to create report with.", "Missing Plugin", JOptionPane.PLAIN_MESSAGE);
			return false;
		}
		final ForecastReportingPlugin fPlugin = plugin;
		List<EnsembleReportInfo> ensembleInfos = getEnsembleInfos();
		if ( ensembleInfos.size() == 0 )
		{
			JOptionPane.showMessageDialog(this, "No Ensemble Sets selected to report on, or no members entered.", "Nothing to Report on", JOptionPane.PLAIN_MESSAGE);
			return false;
		}
		_agp = new AnimatedWaitGlassPane();
		_agp.setTransparency(0.8f);
		setGlassPane("Creating Reports...");
		try
		{

			int maxThreads = 1;
			ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);


			SwingWorker<Void, ReportCreator> worker = new SwingWorker<Void, ReportCreator>()
			{
				private boolean _successful = true;
				private ReportCreator _failedReport;
				@Override
				public Void doInBackground()
				{
					if ( _reportTypeCombo.getSelectedIndex() > 0 )
					{
						TemplateWrapper wrapper = (TemplateWrapper) _reportTypeCombo.getSelectedItem();
						_sims.get(0).setReportCsvFile(wrapper.getPath());
					}
					Future<ReportCreator> future = createReport(fPlugin, _sims.get(0), ensembleInfos);


					List<Future<ReportCreator>>futures = new ArrayList<>();

					if ( future != null )
					{
						futures.add(future);
					}

					ReportCreator rv;
					for (int i = 0;i < futures.size(); i++ )
					{
						try
						{
							rv = futures.get(i).get();
							publish(rv);
						}
						catch (InterruptedException | ExecutionException e)
						{
							e.printStackTrace();
						}
					}


					return null;
				}
				private Future<ReportCreator> createReport(ForecastReportingPlugin reportPlugin, SimulationReportInfo sris, List<EnsembleReportInfo>esetInfo)
				{
					ReportCreator rc = new ReportCreator(reportPlugin, sris, esetInfo);
					Future<ReportCreator> future = threadPool.submit(rc);
					return future;
				}
				@Override
				public void process(List<ReportCreator> chunks)
				{
					if ( chunks == null || chunks.isEmpty())
					{
						return;
					}
					ReportCreator rv;
					for (int i = 0;i < chunks.size(); i++ )
					{
						rv = chunks.get(i);
						if ( rv != null )
						{
							if ( !rv.wasReportSuccessFul())
							{
								_agp.setMessage("Failed to create report "+rv.getReportPlugin().getName());
								_successful = false;
								_failedReport = rv;
							}
						}
						else
						{
							_agp.setMessage("Failed to create report ");
							_successful = false;
							_failedReport = rv;
						}
					}
				}
				@Override
				public void done()
				{
					_agp.setMessage("Reports Complete");
					try
					{
						threadPool.awaitTermination(5, TimeUnit.SECONDS);
					}
					catch (InterruptedException e)
					{
					}
					try
					{

					}
					finally
					{
						resetGlassPane();
					}
					if ( _successful )
					{
						int opt = JOptionPane.showOptionDialog(DisplayForecastReportsSelector.this, "Reports Created Successfully",
								"Complete",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,
								null, new Object[] {"Close", "Display Reports"}, "Close");
						if ( opt == 1 )
						{
							DisplayReportAction action = new DisplayReportAction(_parentPanel);
							action.displayReportAction();
						}
					}
					else
					{
						JOptionPane.showMessageDialog(DisplayForecastReportsSelector.this,
								"Failed to create report for "+_failedReport._reportPlugin.getName(),
								"Report Failed", JOptionPane.INFORMATION_MESSAGE);
					}
				}
			};
			worker.execute();

		}
		catch (Exception e )
		{
			Logger.getLogger(DisplayForecastReportsSelector.class.getName()).warning("Exception running reports " + e);
			resetGlassPane();
		}
		finally
		{


		}

		return true;
	}
	public void setGlassPane(String msg)
	{
		_glassPane = getGlassPane();
		_agp = new AnimatedWaitGlassPane();
		_agp.setColor(Color.BLACK);
		setGlassPane(_agp);
		_agp.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		_agp.setMessage(msg);
		_agp.setActive(true);
		_agp.setVisible(true);

	}

	public void resetGlassPane()
	{
		if(_agp != null && _glassPane != null)
		{
			_agp.setCursor(Cursor.getDefaultCursor());
			_agp.setActive(false);
			_agp.setVisible(false);
			setGlassPane(_glassPane);
			_agp = null;
			_glassPane = null;
		}
	}

	public class ReportCreator
			implements Callable
	{

		private final List<EnsembleReportInfo> _esetInfo;
		ForecastReportingPlugin _reportPlugin;
		private boolean _reportRv;
		private SimulationReportInfo _sris;

		/**
		 * @param reportPlugin
		 * @param sris
		 */
		public ReportCreator(ForecastReportingPlugin reportPlugin, SimulationReportInfo sris, List<EnsembleReportInfo> esetInfo)
		{
			super();
			_reportPlugin = reportPlugin;
			_sris = sris;
			_esetInfo = esetInfo;
		}

		@Override
		public Object call() throws Exception
		{
			_agp.setMessage("Creating report for "+_reportPlugin.getName());

			ReportOptions options = _optionsPanel.getReportOptions();
			try
			{
				_reportRv = _reportPlugin.createReport(_sris, _esetInfo, options);
			}
			catch ( Exception e )
			{
				Logger.getLogger(DisplayForecastReportsSelector.class.getName()).info("Failed to run report "+_reportPlugin.getName()
						+" Error:"+e);
				e.printStackTrace();
				_reportRv = false;
			}
			return this;
		}

		public boolean wasReportSuccessFul()
		{
			return _reportRv;
		}

		/**
		 * @return
		 */
		public ReportPlugin getReportPlugin()
		{
			return _reportPlugin;
		}


	}
	private void fillReportTemplateCombo(ReportPlugin selectedPlugin)
	{

		if (selectedPlugin == null )
		{
			return;
		}
		String prjDir = Project.getCurrentProject().getProjectDirectory();
		String dir = RMAIO.concatPath(prjDir, REPORTS_DIRS);
		dir = RMAIO.concatPath(dir, RMAIO.userNameToFileName(selectedPlugin.getName()));

		List<String> templates = FileManagerImpl.getFileManager().list(dir, new RMAFilenameFilter("csv", "report CSV files"));
		if ( templates != null )
		{
			List<TemplateWrapper> templatesList = templates.stream().map(e -> new TemplateWrapper(e)).collect(Collectors.toList());
			Vector<TemplateWrapper> vec = new Vector<>(templatesList);
			TemplateWrapper emptyWrapper = new TemplateWrapper("");
			vec.add(0, emptyWrapper);
			_reportTypeCombo.setModel(new RmaListModel(true,vec ));
		}
	}

}
