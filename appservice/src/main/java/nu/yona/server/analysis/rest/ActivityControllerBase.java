package nu.yona.server.analysis.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonFormat;

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

	protected final String WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT = "/weeks/";
	protected final String DAY_OVERVIEWS_URI_FRAGMENT = "/days/";
	protected final String WEEK_ACTIVITY_DETAIL_URI_FRAGMENT = "/weeks/{date}/details/{goalID}";
	protected final String DAY_ACTIVITY_DETAIL_URI_FRAGMENT = "/days/{date}/details/{goalID}";
	protected final String GOAL_PATH_VARIABLE = "goalID";
	protected final String DATE_PATH_VARIABLE = "date";

	protected HttpEntity<PagedResources<WeekActivityOverviewResource>> getWeekActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable, PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler,
			Supplier<Page<WeekActivityOverviewDTO>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
						new WeekActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK));
	}

	protected HttpEntity<PagedResources<DayActivityOverviewResource>> getDayActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable, PagedResourcesAssembler<DayActivityOverviewDTO> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
						new DayActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK));
	}

	protected HttpEntity<WeekActivityResource> getWeekActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, WeekActivityDTO> activitySupplier, LinkProvider linkProvider)
	{
		LocalDate date = WeekActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						new WeekActivityResourceAssembler(linkProvider, true).toResource(activitySupplier.apply(date)),
						HttpStatus.OK));
	}

	protected HttpEntity<DayActivityResource> getDayActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, DayActivityDTO> activitySupplier, LinkProvider linkProvider)
	{
		LocalDate date = DayActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						new DayActivityResourceAssembler(linkProvider, true, true).toResource(activitySupplier.apply(date)),
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

		@JsonFormat(shape = JsonFormat.Shape.OBJECT)
		public Map<DayOfWeek, DayActivityResource> getDayActivities()
		{
			DayActivityResourceAssembler a = new DayActivityResourceAssembler(linkProvider, false, false);
			return getContent().getDayActivities().entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> a.toResource(e.getValue())));
		}
	}

	static class WeekActivityOverviewResource extends Resource<WeekActivityOverviewDTO>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResource(LinkProvider linkProvider, WeekActivityOverviewDTO weekActivityOverview)
		{
			super(weekActivityOverview);
			this.linkProvider = linkProvider;
		}

		public List<WeekActivityResource> getWeekActivities()
		{
			return new WeekActivityResourceAssembler(linkProvider, false).toResources(getContent().getWeekActivities());
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

		public DayActivityOverviewResource(LinkProvider linkProvider, DayActivityOverviewDTO dayActivityOverview)
		{
			super(dayActivityOverview);
			this.linkProvider = linkProvider;
		}

		public List<DayActivityResource> getDayActivities()
		{
			return new DayActivityResourceAssembler(linkProvider, true, false).toResources(getContent().getDayActivities());
		}
	}

	static class WeekActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<WeekActivityOverviewDTO, WeekActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, WeekActivityOverviewResource.class);
			this.linkProvider = linkProvider;
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
			return new WeekActivityOverviewResource(linkProvider, weekActivityOverview);
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDTO, WeekActivityResource>
	{
		private final LinkProvider linkProvider;
		private boolean addSelfLink;

		public WeekActivityResourceAssembler(LinkProvider linkProvider, boolean addSelfLink)
		{
			super(ActivityControllerBase.class, WeekActivityResource.class);
			this.linkProvider = linkProvider;
			this.addSelfLink = addSelfLink;
		}

		@Override
		public WeekActivityResource toResource(WeekActivityDTO weekActivity)
		{
			WeekActivityResource weekActivityResource = instantiateResource(weekActivity);
			if (addSelfLink)
			{
				addWeekDetailsLink(weekActivityResource, Link.REL_SELF);
			}
			else
			{
				addWeekDetailsLink(weekActivityResource, "weekDetails");
			}
			addGoalLink(weekActivityResource);
			return weekActivityResource;
		}

		@Override
		protected WeekActivityResource instantiateResource(WeekActivityDTO weekActivity)
		{
			return new WeekActivityResource(linkProvider, weekActivity);
		}

		private void addWeekDetailsLink(WeekActivityResource weekActivityResource, String rel)
		{
			weekActivityResource.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getDate(),
					weekActivityResource.getContent().getGoalID()).withRel(rel));
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
		private boolean addSelfLink;

		public DayActivityResourceAssembler(LinkProvider linkProvider, boolean addGoalLink, boolean addSelfLink)
		{
			super(ActivityControllerBase.class, DayActivityResource.class);
			this.linkProvider = linkProvider;
			this.addGoalLink = addGoalLink;
			this.addSelfLink = addSelfLink;
		}

		@Override
		public DayActivityResource toResource(DayActivityDTO dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateResource(dayActivity);
			if (addGoalLink)
			{
				addGoalLink(dayActivityResource);
			}
			if (addSelfLink)
			{
				addDayDetailsLink(dayActivityResource, Link.REL_SELF);
			}
			else
			{
				addDayDetailsLink(dayActivityResource, "dayDetails");
			}
			return dayActivityResource;
		}

		@Override
		protected DayActivityResource instantiateResource(DayActivityDTO dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getGoalLinkBuilder(dayActivityResource.getContent().getGoalID()).withRel("goal"));
		}

		private void addDayDetailsLink(DayActivityResource dayActivityResource, String rel)
		{
			dayActivityResource.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getDate(),
					dayActivityResource.getContent().getGoalID()).withRel(rel));
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<DayActivityOverviewDTO, DayActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, DayActivityOverviewResource.class);
			this.linkProvider = linkProvider;
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
			return new DayActivityOverviewResource(linkProvider, dayActivityOverview);
		}
	}
}
