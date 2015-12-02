package nu.yona.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;

@Service
public class GoalFileLoader implements ApplicationListener<ContextRefreshedEvent>
{
	private static final Logger LOGGER = Logger.getLogger(GoalFileLoader.class.getName());

	@Autowired
	private GoalService goalService;

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event)
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
