package nu.yona.server;

import static java.util.logging.Level.WARNING;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.Logger;

import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		try
		{
			try (InputStream input = new FileInputStream("data/goals.json"))
			{
				ObjectMapper mapper = new ObjectMapper();
				Set<GoalDTO> goalsFromFile = mapper.readValue(input, new TypeReference<Set<GoalDTO>>() {
				});
				goalService.importGoals(goalsFromFile);
			}
		}
		catch (IOException e)
		{
			LOGGER.log(WARNING, "Error loading goals from file " + e);
		}
	}
}
