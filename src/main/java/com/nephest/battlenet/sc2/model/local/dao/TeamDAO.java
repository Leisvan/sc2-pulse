// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class TeamDAO
{

    private  static final Logger LOG = LoggerFactory.getLogger(TeamDAO.class);

    public static final String STD_SELECT =
        "team.id AS \"team.id\", "
        + "team.division_id AS \"team.division_id\", "
        + "team.battlenet_id AS \"team.battlenet_id\", "
        + "team.season AS \"team.season\", "
        + "team.region AS \"team.region\", "
        + "team.league_type AS \"team.league_type\", "
        + "team.queue_type AS \"team.queue_type\", "
        + "team.team_type AS \"team.team_type\", "
        + "team.tier_type AS \"team.tier_type\", "
        + "team.rating AS \"team.rating\", "
        + "team.wins AS \"team.wins\", "
        + "team.losses AS \"team.losses\", "
        + "team.ties AS \"team.ties\", "
        + "team.points AS \"team.points\", "
        + "team.global_rank AS \"team.global_rank\", "
        + "team.region_rank AS \"team.region_rank\", "
        + "team.league_rank AS \"team.league_rank\" ";

    private static final String CREATE_QUERY = "INSERT INTO team "
        + "("
            + "division_id, battlenet_id, "
            + "season, region, league_type, queue_type, team_type, tier_type, "
            + "rating, points, wins, losses, ties"
        + ") "
        + "VALUES ("
            + ":divisionId, :battlenetId, "
            + ":season, :region, :leagueType, :queueType, :teamType, :tierType, "
            + ":rating, :points, :wins, :losses, :ties"
        + ")";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(region, battlenet_id) DO UPDATE SET "
        + "division_id=excluded.division_id, "
        + "season=excluded.season, "
        + "league_type=excluded.league_type, "
        + "queue_type=excluded.queue_type, "
        + "team_type=excluded.team_type, "
        + "tier_type=excluded.tier_type, "
        + "rating=excluded.rating, "
        + "points=excluded.points, "
        + "wins=excluded.wins, "
        + "losses=excluded.losses, "
        + "ties=excluded.ties";

    private static final String CALCULATE_RANK_TEMPLATE =
        "WITH cte AS"
        + "("
            + "SELECT id, RANK() OVER(PARTITION BY queue_type, team_type%2$s ORDER BY rating DESC, id DESC) as rnk "
            + "FROM team "
            + "WHERE season = :season "
        + ")"
        + "UPDATE team "
        + "set %1$s_rank=cte.rnk "
        + "FROM cte "
        + "WHERE team.id = cte.id "
        + "AND team.%1$s_rank != cte.rnk";

    private static final String CALCULATE_GLOBAL_RANK_QUERY = String.format(CALCULATE_RANK_TEMPLATE, "global", "");
    private static final String CALCULATE_REGION_RANK_QUERY =
        String.format(CALCULATE_RANK_TEMPLATE, "region", ", region");
    private static final String CALCULATE_LEAGUE_RANK_QUERY =
        String.format(CALCULATE_RANK_TEMPLATE, "league", ", league_type");

    private static RowMapper<Team> STD_ROW_MAPPER;
    private static ResultSetExtractor<Team> STD_EXTRACTOR;

    public static final ResultSetExtractor<Optional<Map.Entry<Team, List<TeamMember>>>> BY_FAVOURITE_RACE_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return Optional.empty();

        return Optional.of(new AbstractMap.SimpleEntry<Team,List<TeamMember>>(
            TeamDAO.getStdRowMapper().mapRow(rs, 0),
            List.of(TeamMemberDAO.STD_ROW_MAPPER.mapRow(rs, 0))));
    };


    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public TeamDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new Team
        (
            rs.getLong("team.id"),
            rs.getInt("team.season"),
            conversionService.convert(rs.getInt("team.region"), Region.class),
            new BaseLeague
            (
                conversionService.convert(rs.getInt("team.league_type"), League.LeagueType.class),
                conversionService.convert(rs.getInt("team.queue_type"), QueueType.class),
                conversionService.convert(rs.getInt("team.team_type"), TeamType.class)
            ),
            conversionService.convert(rs.getInt("team.tier_type"), LeagueTier.LeagueTierType.class),
            rs.getLong("team.division_id"),
            ((BigDecimal) rs.getObject("team.battlenet_id")).toBigInteger(),
            rs.getLong("team.rating"),
            rs.getInt("team.wins"), rs.getInt("team.losses"), rs.getInt("team.ties"),
            rs.getInt("team.points")
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = (rs)->
        {
            if(!rs.next()) return null;
            return getStdRowMapper().mapRow(rs, 0);
        };
    }

    public static RowMapper<Team> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Team> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public Team create(Team team)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(team);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        team.setId(keyHolder.getKey().longValue());
        return team;
    }

    public Team merge(Team team)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(team);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        team.setId(keyHolder.getKey().longValue());
        return team;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateRanks(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("season", season);
        template.update(CALCULATE_GLOBAL_RANK_QUERY, params);
        template.update(CALCULATE_REGION_RANK_QUERY, params);
        template.update(CALCULATE_LEAGUE_RANK_QUERY, params);
        LOG.debug("Calculated team ranks for {} season", season);
    }

    private MapSqlParameterSource createParameterSource(Team team)
    {
        return new MapSqlParameterSource()
            .addValue("divisionId", team.getDivisionId())
            .addValue("battlenetId", team.getBattlenetId())
            .addValue("season", team.getSeason())
            .addValue("region", conversionService.convert(team.getRegion(), Integer.class))
            .addValue("leagueType", conversionService.convert(team.getLeagueType(), Integer.class))
            .addValue("queueType", conversionService.convert(team.getQueueType(), Integer.class))
            .addValue("teamType", conversionService.convert(team.getTeamType(), Integer.class))
            .addValue("tierType", conversionService.convert(team.getTierType(), Integer.class))
            .addValue("rating", team.getRating())
            .addValue("points", team.getPoints())
            .addValue("wins", team.getWins())
            .addValue("losses", team.getLosses())
            .addValue("ties", team.getTies());
    }

}
