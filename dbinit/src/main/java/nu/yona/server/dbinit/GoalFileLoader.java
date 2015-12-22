package nu.yona.server.dbinit;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(GoalFileLoader.class);

	private final String GOALS_FILE = "data/goals.json";

	@Autowired
	private GoalService goalService;

	@Override
	public void run(String... args) throws Exception
	{
		loadGoalsFromFile();
	}

	private void loadGoalsFromFile()
	{
		try (InputStream input = new FileInputStream(GOALS_FILE))
		{
			logger.info("Loading goals from file '{}' in directory '{}'", GOALS_FILE, System.getProperty("user.dir"));
			ObjectMapper mapper = new ObjectMapper();
			Set<GoalDTO> goalsFromFile = mapper.readValue(input, new TypeReference<Set<GoalDTO>>() {
			});
			goalService.importGoals(goalsFromFile);
			logger.info("Goals loaded successfully");
		}
		catch (IOException e)
		{
			logger.error("Error loading goals from file '" + GOALS_FILE + "'", e);
			throw GoalFileLoaderException.loadingGoalsFromFile(e, GOALS_FILE);
		}
	}
}
