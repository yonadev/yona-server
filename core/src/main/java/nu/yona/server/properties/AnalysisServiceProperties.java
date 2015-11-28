package nu.yona.server.properties;

public class AnalysisServiceProperties
{
	private long conflictInterval = 300000L;
	private long updateSkipWindow = 5000L;

	public long getConflictInterval()
	{
		return conflictInterval;
	}

	public void setConflictInterval(long conflictInterval)
	{
		this.conflictInterval = conflictInterval;
	}

	public long getUpdateSkipWindow()
	{
		return updateSkipWindow;
	}

	public void setUpdateSkipWindow(long updateSkipWindow)
	{
		this.updateSkipWindow = updateSkipWindow;
	}
}
