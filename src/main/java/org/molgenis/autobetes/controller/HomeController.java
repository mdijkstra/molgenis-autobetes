package org.molgenis.autobetes.controller;

import static org.molgenis.autobetes.controller.HomeController.URI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.molgenis.autobetes.MovesConnector;
import org.molgenis.autobetes.MovesConnectorImpl;
import org.molgenis.autobetes.pumpobjectsparser.BasalProfileDefinitionGroupParser;
import org.molgenis.autobetes.pumpobjectsparser.BasalProfileDefinitionParser;
import org.molgenis.autobetes.pumpobjectsparser.BasalProfileStartParser;
import org.molgenis.autobetes.pumpobjectsparser.BgMeterParser;
import org.molgenis.autobetes.pumpobjectsparser.BgSensorParser;
import org.molgenis.autobetes.pumpobjectsparser.BolusNormalParser;
import org.molgenis.autobetes.pumpobjectsparser.BolusSquareParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeCarbRatioGroupParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeCarbRatioParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeInsulinSensitivityGroupParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeInsulinSensitivityParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeSuspendEnableParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeTempBasalParser;
import org.molgenis.autobetes.pumpobjectsparser.ChangeTempBasalPercentParser;
import org.molgenis.autobetes.pumpobjectsparser.TimeChangeParser;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.framework.ui.MolgenisPluginController;
import org.molgenis.omx.auth.MolgenisUser;
import org.molgenis.security.core.utils.SecurityUtils;
import org.molgenis.util.FileStore;
import org.molgenis.data.DataService;
import org.molgenis.framework.ui.MolgenisPluginController;
import org.molgenis.omx.converters.ValueConverterException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller that handles home page requests
 */
@Controller
@RequestMapping(URI)
public class HomeController extends MolgenisPluginController
{
	public static final String ID = "home";
	public static final String URI = MolgenisPluginController.PLUGIN_URI_PREFIX + ID;
	public static final String BASE_URI = "";
	

	
	// private static int BASALTIMESTEP = 3 * 60 * 1000; // 3 min
	private static String HEADER = "HEADER";
	private static String BODY = "BODY";

	private final static String RAWTYPE = "Onbewerkt: type";
//	private static String RAWVALUES = "Onbewerkt: waarden";

	private final static String ChangeTimeGH = "ChangeTimeGH";
	private final static String CalBGForPH = "CalBGForPH";
	private final static String GlucoseSensorData = "GlucoseSensorData";
	private final static String BolusNormal = "BolusNormal";
	private final static String BolusSquare = "BolusSquare";
	private final static String ChangeBasalProfilePattern = "ChangeBasalProfilePattern";
	private final static String ChangeBasalProfile = "ChangeBasalProfile";
	private final static String BasalProfileStart = "BasalProfileStart";
	private final static String ChangeTempBasal = "ChangeTempBasal";
	private final static String ChangeTempBasalPercent = "ChangeTempBasalPercent";
	private final static String ChangeCarbRatioPattern = "ChangeCarbRatioPattern";
	private final static String ChangeCarbRatio = "ChangeCarbRatio";
	private final static String ChangeInsulinSensitivityPattern = "ChangeInsulinSensitivityPattern";
	private final static String ChangeInsulinSensitivity = "ChangeInsulinSensitivity";
	private final static String ChangeSuspendEnable = "ChangeSuspendEnable";

	@Autowired
	private DataService dataService;

	@Autowired
	private JavaMailSender mailSender;
	
	@Autowired
	private FileStore fileStore;

	public HomeController()
	{
		super(URI);
	}

	@RequestMapping
	public String init()
	{
		return "view-home";
	}

	@RequestMapping(value = "/uploadCSV", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadCSV( @RequestParam
	Part file, Model model, HttpServletRequest servletRequest)
	{
		String username = SecurityUtils.getCurrentUsername();
		MolgenisUser user = dataService.findOne(MolgenisUser.ENTITY_NAME, new QueryImpl().eq(MolgenisUser.USERNAME, username), MolgenisUser.class);
		try
		{
			File uploadFile = fileStore.store(file.getInputStream(), file.getName());
			
			
			String baseDir = "/Users/dionkoolhaas/PumpCSVFiles/";
			String tmpDir = baseDir + "tmp/";
			String outputDir = baseDir + "split/";
			split(uploadFile, new File(outputDir), tmpDir);
			//import activities from moves
			MovesConnector movesConnector = new MovesConnectorImpl();
			movesConnector.manageActivities(dataService, user);
			model.addAttribute("message", "great succes!!");
			//System.out.println(IOUtils.toString(file.getInputStream(), "UTF-8"));
			
			//List<File> uploadedFiles = ZipFileUtil.unzip(uploadFile);
			//if (uploadedFiles.size() > 0) ontologyIndexer.index(new OntologyLoader(ontologyName, uploadedFiles.get(0)));
			//model.addAttribute("isIndexRunning", true);
		}
		catch (Exception e)
		{
			model.addAttribute("message", "Please upload a valid zip file!");
			model.addAttribute("isCorrectZipFile", false);
		}
		
		return "view-home";
	}

	private void split(File inputFile, File outputDir, String tmpDir)
	{
		// List<Exercise> exerciseListFile = new ArrayList<Exercise>();
		// List<Carbs> carbListFile = new ArrayList<Carbs>();
		// List<BgSensor> bgsensorListFile = new ArrayList<BgSensor>();
		// List<Bolus> bolusListFile = new ArrayList<Bolus>();
		// List<BasalProgrammed> basalProgrammedListFile = new ArrayList<BasalProgrammed>();
		// List<BasalTemp> basalTempListFile = new ArrayList<BasalTemp>();
		// List<BasalSetting> basalList = new ArrayList<BasalSetting>();
		// List<Basal> basalAsReleased = new ArrayList<Basal>();
		// List<BgSensor> bgSensorList = new ArrayList<BgSensor>();

		// define 'unique' body file name
		String random = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 4);
		File bodyFile = new File(tmpDir + random + ".txt");

		// split header and body
		try
		{
			LinkedHashMap<String, String> fsplit;
			fsplit = splitInHeaderTail(inputFile);
			// work around: save as file so that we can read it in again with csvReader..
			FileUtils.writeStringToFile(bodyFile, fsplit.get(BODY));
		}
		catch (IOException e)
		{
			System.err.println(">> ERROR >> when (1) reading input (2) splitting in header/body (3) and save body");
			e.printStackTrace();
		}

		// read in body
		CsvRepository csvRepo = new CsvRepository(bodyFile, null, ';');

		
		// get current owner
		String userName = SecurityUtils.getCurrentUsername();
		MolgenisUser molgenisUser = (MolgenisUser) dataService.findOne(MolgenisUser.ENTITY_NAME, new QueryImpl().eq(MolgenisUser.USERNAME, userName));
		
		// list stores which entities cannot be loaded so that we do not show duplicates
		Set<String> rawTypeSet = new HashSet<String>();
		System.out.println("To do:");
		for (Entity e : csvRepo)
		{
			String rawType = (String) e.get(RAWTYPE);
//			System.out.println(">> Parsing: " + rawType + ": " + e.toString());
			switch (rawType)
			{
				// TODO Probably this is not the right variable! Use 'volgnummer' or 'pumpID' to determine which one to take!
				case ChangeTimeGH:
					new TimeChangeParser(e, dataService, molgenisUser);
					break;

				case CalBGForPH:
					new BgMeterParser(e, dataService, molgenisUser);
					break;
					
				case GlucoseSensorData:
					new BgSensorParser(e, dataService, molgenisUser);
					break;

				case BolusNormal:
					new BolusNormalParser(e, dataService, molgenisUser);
					break;

				case BolusSquare:
					new BolusSquareParser(e, dataService, molgenisUser);
					break;

				case ChangeBasalProfilePattern: // postfix 'Pre' means 'previous'
					new BasalProfileDefinitionGroupParser(e, dataService, molgenisUser);
					break;

				case ChangeBasalProfile: // postfix 'Pre' means 'previous'
					new BasalProfileDefinitionParser(e, dataService, molgenisUser);
					break;

				case BasalProfileStart:
					new BasalProfileStartParser(e, dataService, molgenisUser);
					break;

				case ChangeTempBasal:
					new ChangeTempBasalParser(e, dataService, molgenisUser);
					break;

				case ChangeTempBasalPercent:
					new ChangeTempBasalPercentParser(e, dataService, molgenisUser);
					break;

				case ChangeCarbRatioPattern:
					new ChangeCarbRatioGroupParser(e, dataService, molgenisUser);
					break;

				case ChangeCarbRatio:
					new ChangeCarbRatioParser(e, dataService, molgenisUser);
					break;

				case ChangeInsulinSensitivityPattern:
					new ChangeInsulinSensitivityGroupParser(e, dataService, molgenisUser);
					break;

				case ChangeInsulinSensitivity:
					new ChangeInsulinSensitivityParser(e, dataService, molgenisUser);
					break;

				case ChangeSuspendEnable:
					new ChangeSuspendEnableParser(e, dataService, molgenisUser);
					break;					
					
				default:
					if (!rawTypeSet.contains(rawType))
					{
						System.out.println(rawType);// + ":   " + e.toString());
						rawTypeSet.add(rawType);
					}
					break;
			}
		}

		IOUtils.closeQuietly(csvRepo);
	}

	/*
	 * Split file in header and body
	 */
	private static LinkedHashMap<String, String> splitInHeaderTail(File f) throws IOException
	{
		String content = fileToString(f);

		int lineIndex = 0, positionNewline = 0, nHeaderLines = 11;

		// TODO do this smarter; e.g. assume header ends when number of separaters is
		// big (or maybe even equal to a certain number)

		for (positionNewline = content.indexOf("\n"); positionNewline != -1 && lineIndex < nHeaderLines - 1; positionNewline = content
				.indexOf("\n", positionNewline + 1))
		{
			lineIndex++;
		}

		String header = content.substring(0, positionNewline + 1);
		String body = content.substring(positionNewline + 1);

		LinkedHashMap<String, String> fsplit = new LinkedHashMap<String, String>();
		fsplit.put(HEADER, header);
		fsplit.put(BODY, body);

		return fsplit;
	}

	private static String fileToString(File f) throws IOException
	{
		FileInputStream stream = new FileInputStream(f);
		try
		{
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			/* Instead of using default, pass in a decoder. */
			return Charset.defaultCharset().decode(bb).toString();
		}
		finally
		{
			stream.close();
		}
	}
	
	@RequestMapping("upload-csv")
	public String uploadForm() throws InterruptedException
	{
		return "view-upload-pump-csv";
	}

	@RequestMapping("view-report")
	public String viewLogo() throws InterruptedException
	{
		return "view-report";
	}

	@RequestMapping("upload")
	public String upload() throws InterruptedException
	{
		return "view-upload";
	}

	@RequestMapping(method = RequestMethod.POST, value = "/validate")
	@PreAuthorize("hasAnyRole('ROLE_SU')")
	public String validate(HttpServletRequest request, @RequestParam("csvFile") MultipartFile csvFile, Model model)
			throws IOException, ValueConverterException, MessagingException, Exception
	{
		System.err.println("in validate...");
		boolean submitState = false;
		String action = "/validate";
		String enctype = "multipart/form-data";

		final List<String> messages = new ArrayList<String>();
		if (!csvFile.isEmpty())
		{
			try
			{
				System.err.println("Gelukt!");
			}
			catch (Exception e)
			{
				System.err.println("FOUT!");
			}
		}
		else
		{
			String errorMessage = "The file you try to upload is empty! Filename: " + csvFile.getOriginalFilename();
			// messages.add(errorMessage);
			System.err.println(errorMessage);
		}
		model.addAttribute("action", action);
		model.addAttribute("enctype", enctype);
		model.addAttribute("submit_state", submitState);
		model.addAttribute("messages", messages);
		return "view-upload";
	}
}
