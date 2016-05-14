package nu.yona.server.analysis.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.service.ActivityService;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.WeekActivityDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Activity controller base class.
 */
abstract class ActivityControllerBase
{
	@Autowired
	protected ActivityService activityService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	protected final String WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT = "/weeks/";
	protected final String DAY_OVERVIEWS_URI_FRAGMENT = "/days/";
	protected final String WEEK_ACTIVITY_DETAIL_URI_FRAGMENT = "/weeks/{date}/details/{goalID}";
	protected final String DAY_ACTIVITY_DETAIL_URI_FRAGMENT = "/days/{date}/details/{goalID}";
	protected final String GOAL_PATH_VARIABLE = "goalID";
	protected final String DATE_PATH_VARIABLE = "date";

	protected HttpEntity<PagedResources<WeekActivityOverviewResource>> getWeekActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable, PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler,
			Supplier<Page<WeekActivityOverviewDTO>> activityFunction, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activityFunction.get(),
						new WeekActivityOverviewResourceAssembler(linkProvider, curieProvider)), HttpStatus.OK));
	}

	protected HttpEntity<PagedResources<DayActivityOverviewResource>> getDayActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable, PagedResourcesAssembler<DayActivityOverviewDTO> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO>> activityFunction, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activityFunction.get(),
						new DayActivityOverviewResourceAssembler(linkProvider, curieProvider)), HttpStatus.OK));
	}

	protected HttpEntity<WeekActivityResource> getWeekActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, WeekActivityDTO> activityFunction, LinkProvider linkProvider)
	{
		LocalDate date = WeekActivityDTO.parseDate(dateStr);
		return CryptoSession
				.execute(password, () -> userService.canAccessPrivateData(userID),
						() -> new ResponseEntity<>(
								new WeekActivityResourceAssembler(linkProvider).toResource(activityFunction.apply(date)),
								HttpStatus.OK));
	}

	protected HttpEntity<DayActivityResource> getDayActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, DayActivityDTO> activityFunction, LinkProvider linkProvider)
	{
		LocalDate date = DayActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						new DayActivityResourceAssembler(linkProvider, true).toResource(activityFunction.apply(date)),
						HttpStatus.OK));
	}

	interface LinkProvider
	{
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalID);

		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalID);

		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalID);
	}

	static class WeekActivityResource extends Resource<WeekActivityDTO>
	{
		private final LinkProvider linkProvider;

		public WeekActivityResource(LinkProvider linkProvider, WeekActivityDTO weekActivity)
		{
			super(weekActivity);
			this.linkProvider = linkProvider;
		}

		@JsonProperty("_embedded")
		public Map<DayOfWeek, DayActivityResource> getEmbeddedResources()
		{
			DayActivityResourceAssembler a = new DayActivityResourceAssembler(linkProvider, false);
			return getContent().getDayActivities().entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> a.toResource(e.getValue())));
		}
	}

	static class WeekActivityOverviewResource extends Resource<WeekActivityOverviewDTO>
	{
		private final LinkProvider linkProvider;
		private final CurieProvider curieProvider;

		public WeekActivityOverviewResource(LinkProvider linkProvider, CurieProvider curieProvider,
				WeekActivityOverviewDTO weekActivityOverview)
		{
			super(weekActivityOverview);
			this.linkProvider = linkProvider;
			this.curieProvider = curieProvider;
		}

		@JsonProperty("_embedded")
		public Map<String, List<WeekActivityResource>> getEmbeddedResources()
		{
			HashMap<String, List<WeekActivityResource>> result = new HashMap<String, List<WeekActivityResource>>();
			result.put(curieProvider.getNamespacedRelFor("weekActivities"),
					new WeekActivityResourceAssembler(linkProvider).toResources(getContent().getWeekActivities()));
			return result;
		}
	}

	static class DayActivityResource extends Resource<DayActivityDTO>
	{
		public DayActivityResource(DayActivityDTO dayActivity)
		{
			super(dayActivity);
		}

		// TODO: embed messages if included on this detail level
	}

	static class DayActivityOverviewResource extends Resource<DayActivityOverviewDTO>
	{
		private final LinkProvider linkProvider;
		private final CurieProvider curieProvider;

		public DayActivityOverviewResource(LinkProvider linkProvider, CurieProvider curieProvider,
				DayActivityOverviewDTO dayActivityOverview)
		{
			super(dayActivityOverview);
			this.linkProvider = linkProvider;
			this.curieProvider = curieProvider;
		}

		@JsonProperty("_embedded")
		public Map<String, List<DayActivityResource>> getEmbeddedResources()
		{
			HashMap<String, List<DayActivityResource>> result = new HashMap<String, List<DayActivityResource>>();
			result.put(curieProvider.getNamespacedRelFor("dayActivities"),
					new DayActivityResourceAssembler(linkProvider, true).toResources(getContent().getDayActivities()));
			return result;
		}
	}

	static class WeekActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<WeekActivityOverviewDTO, WeekActivityOverviewResource>
	{
		private final LinkProvider linkProvider;
		private final CurieProvider curieProvider;

		public WeekActivityOverviewResourceAssembler(LinkProvider linkProvider, CurieProvider curieProvider)
		{
			super(ActivityControllerBase.class, WeekActivityOverviewResource.class);
			this.linkProvider = linkProvider;
			this.curieProvider = curieProvider;
		}

		@Override
		public WeekActivityOverviewResource toResource(WeekActivityOverviewDTO weekActivityOverview)
		{
			WeekActivityOverviewResource weekActivityOverviewResource = instantiateResource(weekActivityOverview);
			return weekActivityOverviewResource;
		}

		@Override
		protected WeekActivityOverviewResource instantiateResource(WeekActivityOverviewDTO weekActivityOverview)
		{
			return new WeekActivityOverviewResource(linkProvider, curieProvider, weekActivityOverview);
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDTO, WeekActivityResource>
	{
		private final LinkProvider linkProvider;

		public WeekActivityResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, WeekActivityResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public WeekActivityResource toResource(WeekActivityDTO weekActivity)
		{
			WeekActivityResource weekActivityResource = instantiateResource(weekActivity);
			addSelfLink(weekActivityResource);
			addGoalLink(weekActivityResource);
			return weekActivityResource;
		}

		@Override
		protected WeekActivityResource instantiateResource(WeekActivityDTO weekActivity)
		{
			return new WeekActivityResource(linkProvider, weekActivity);
		}

		private void addSelfLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getDate(),
					weekActivityResource.getContent().getGoalID()).withSelfRel());
		}

		private void addGoalLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource
					.add(linkProvider.getGoalLinkBuilder(weekActivityResource.getContent().getGoalID()).withRel("goal"));
		}
	}

	static class DayActivityResourceAssembler extends ResourceAssemblerSupport<DayActivityDTO, DayActivityResource>
	{
		private final LinkProvider linkProvider;
		private final boolean addGoalLink;

		public DayActivityResourceAssembler(LinkProvider linkProvider, boolean addGoalLink)
		{
			super(ActivityControllerBase.class, DayActivityResource.class);
			this.linkProvider = linkProvider;
			this.addGoalLink = addGoalLink;
		}

		@Override
		public DayActivityResource toResource(DayActivityDTO dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateResource(dayActivity);
			addSelfLink(dayActivityResource);
			if (addGoalLink)
				addGoalLink(dayActivityResource);
			return dayActivityResource;
		}

		@Override
		protected DayActivityResource instantiateResource(DayActivityDTO dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addSelfLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getDate(),
					dayActivityResource.getContent().getGoalID()).withSelfRel());
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getGoalLinkBuilder(dayActivityResource.getContent().getGoalID()).withRel("goal"));
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<DayActivityOverviewDTO, DayActivityOverviewResource>
	{
		private final LinkProvider linkProvider;
		private final CurieProvider curieProvider;

		public DayActivityOverviewResourceAssembler(LinkProvider linkProvider, CurieProvider curieProvider)
		{
			super(ActivityControllerBase.class, DayActivityOverviewResource.class);
			this.linkProvider = linkProvider;
			this.curieProvider = curieProvider;
		}

		@Override
		public DayActivityOverviewResource toResource(DayActivityOverviewDTO dayActivityOverview)
		{
			DayActivityOverviewResource dayActivityOverviewResource = instantiateResource(dayActivityOverview);
			return dayActivityOverviewResource;
		}

		@Override
		protected DayActivityOverviewResource instantiateResource(DayActivityOverviewDTO dayActivityOverview)
		{
			return new DayActivityOverviewResource(linkProvider, curieProvider, dayActivityOverview);
		}
	}
}
