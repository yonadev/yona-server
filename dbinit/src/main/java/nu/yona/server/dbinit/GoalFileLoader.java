package nu.yona.server.dbinit;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;

@Component
public class GoalFileLoader implements CommandLineRunner
{
	private static final Logger LOGGER = Logger.getLogger(GoalFileLoader.class.getName());

	@Autowired
	private GoalService goalService;

	@Override
	public void run(String... args) throws Exception
	{
		loadGoalsFromFile();
	}

	private void loadGoalsFromFile()
	{
		String inputFileName = "data/goals.json";
		LOGGER.info("Loading goals from file '" + inputFileName + "' in directory '" + System.getProperty("user.dir") + "'");
		try (InputStream input = new FileInputStream(inputFileName))
		{
			ObjectMapper mapper = new ObjectMapper();
			Set<GoalDTO> goalsFromFile = mapper.readValue(input, new TypeReference<Set<GoalDTO>>() {
			});
			goalService.importGoals(goalsFromFile);
		}
		catch (IOException e)
		{
			LOGGER.log(Level.SEVERE, "Error loading goals from file '" + inputFileName + "'", e);
			throw GoalFileLoaderException.loadingGoalsFromFile(e, inputFileName);
		}
	}
}
