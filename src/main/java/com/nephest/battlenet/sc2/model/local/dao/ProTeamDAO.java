// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.ProTeam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;

@Repository
public class ProTeamDAO
extends StandardDAO
{

    private static RowMapper<ProTeam> STD_ROW_MAPPER;

    public static final String STD_SELECT =
        "pro_team.id AS \"pro_team.id\", "
        + "pro_team.aligulac_id AS \"pro_team.aligulac_id\", "
        + "pro_team.name AS \"pro_team.name\", "
        + "pro_team.short_name AS \"pro_team.short_name\", "
        + "pro_team.updated AS \"pro_team.updated\"";
    private static final String CREATE_QUERY =
        "INSERT INTO pro_team (aligulac_id, name, short_name, updated) "
        + "VALUES (:aligulacId, :name, :shortName, :updated)";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES(:aligulacId, :name, :shortName, :updated)), "
        + "updated AS "
        + "("
            + "UPDATE pro_team "
            + "SET "
            + "short_name=v.short_name, "
            + "aligulac_id=v.aligulac_id, "
            + "updated=v.updated "
            + "FROM vals v(aligulac_id, name, short_name, updated) "
            + "WHERE LOWER(REPLACE(pro_team.name, ' ', '')) = LOWER(REPLACE(v.name, ' ', '')) "
            + "RETURNING id"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO pro_team (aligulac_id, name, short_name, updated) "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM updated) "
            + "ON CONFLICT(LOWER(REPLACE(name, ' ', ''))) DO UPDATE SET "
            + "short_name=excluded.short_name, "
            + "aligulac_id=excluded.aligulac_id, "
            + "updated=excluded.updated "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM updated "
        + "UNION "
        + "SELECT id FROM inserted";

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public  ProTeamDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        super(template, "pro_team", "30 DAYS");
        this.template = template;
        initMappers();
    }

    private static void initMappers()
    {
        if (STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) ->
        {
            ProTeam proTeam =  new ProTeam
            (
                rs.getLong("pro_team.id"),
                rs.getLong("pro_team.aligulac_id"),
                rs.getString("pro_team.name"),
                rs.getString("pro_team.short_name")
            );
            proTeam.setUpdated(rs.getObject("pro_team.updated", OffsetDateTime.class));
            return proTeam;
        };
    }

    public static RowMapper<ProTeam> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(ProTeam proTeam)
    {
        return new MapSqlParameterSource()
            .addValue("aligulacId", proTeam.getAligulacId(), Types.BIGINT)
            .addValue("name", proTeam.getName())
            .addValue("shortName", proTeam.getShortName(), Types.VARCHAR)
            .addValue("updated", proTeam.getUpdated());
    }

    public ProTeam merge(ProTeam proTeam)
    {
        proTeam.setUpdated(OffsetDateTime.now());
        MapSqlParameterSource params = createParameterSource(proTeam);
        proTeam.setId(template.query(MERGE_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return proTeam;
    }

    public int[] mergeWithoutIds(ProTeam... proTeams)
    {
        if(proTeams.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[proTeams.length];
        for(int i = 0; i < proTeams.length; i++)
        {
            proTeams[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proTeams[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

}
