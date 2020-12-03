// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class HistoryUtil
{

    static replaceState(obj, title, params)
    {
        Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1] = [title, params];
        HistoryUtil.setObjectLocation(obj, params);
        HistoryUtil.updateState(obj, title, params, true);
    }

    static pushState(obj, title, params)
    {
        Session.titleAndUrlHistory.push([title, params]);
        if(Session.titleAndUrlHistory.length > 2) Session.titleAndUrlHistory.shift();
        HistoryUtil.setObjectLocation(obj, params);
        HistoryUtil.updateState(obj, title, params, false);
    }

    static setObjectLocation(obj, params)
    {
        const hashIx = params.indexOf("#");
        if(hashIx == -1)
        {
            obj.locationSearch = params;
        }
        else
        {
            obj.locationSearch = params.substring(0, hashIx);
            obj.locationHash = params.substring(hashIx)
        }
    }

    static previousTitleAndUrl()
    {
        return Session.titleAndUrlHistory.length > 1
            ? Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 2]
            : Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1];
    }

    static updateState(obj, title, paramsStr, replace)
    {
        const hashIx = paramsStr.indexOf("#");
        const hash = hashIx > -1 ? paramsStr.substring(hashIx + 1) : null;
        HistoryUtil.setParentSectionParameters(hash, paramsStr);
        if(Session.isHistorical) return;
        if(replace)
        {
            history.replaceState(obj, title, paramsStr);
        }
        else
        {
            history.pushState(obj, title, paramsStr);
        }

    }

    static setParentSectionParameters(deepestTabId, paramsStr)
    {
        const params = new URLSearchParams(paramsStr);
        const tabs = [];
        let parentTab;
        if(deepestTabId != null)
        {
            let prevTab = document.getElementById(deepestTabId);
            tabs.push(prevTab);
            while(true)
            {
                const curTab = prevTab.parentNode.closest(".tab-pane");
                if(curTab == null || curTab == prevTab) break;
                tabs.push(curTab);
                if(tabs.length == 2) break;
                prevTab = curTab;
            }
            parentTab = tabs.length == 1 ? tabs[0] : tabs[1];
        }
        let dataTarget;
        if(params.get("type") == "modal")
        {
            dataTarget = "#" + params.get("id");
        }
        else
        {
            dataTarget = tabs.length == 0
                ? "#" + document.querySelector(".modal.show").id
                : "#" + parentTab.id;
            const modal = document.querySelector(dataTarget).closest(".modal");
            dataTarget = modal == null ? dataTarget : "#" + modal.id;
        }
        Session.sectionParams.set(dataTarget, paramsStr.split("#")[0]);
    }

    static getDeepestTabId(el)
    {
        let hash = null;
        const tabs = el.querySelectorAll(":scope .nav-pills a.active");
        for(let i = tabs.length - 1; i > -1; i--)
        {
            const tab = tabs[i];
            if(tab.offsetParent != null)
            {
                hash = tab.getAttribute("data-target").substring(1);
                break;
            }
        }
        return hash;
    }

    static formatSearchString(search, hash)
    {
        if(search == "?") search = "";
        return (search != null && search.length > 0 ? "?" + search : "?")
            + (hash != null && hash.length > 0 ? "#" + hash : "");
    }

    static initActiveTabs()
    {
        const locationSearch = Session.locationSearch();
        if(locationSearch.indexOf("#") != -1 && locationSearch.indexOf("#") != locationSearch.length - 1)
            return; //tabs are explicit, do not touch them

        const params = new URLSearchParams(locationSearch);
        if(params.get("m") != 1)
        {
            Session.lastNonModalParams =  locationSearch;
            Session.lastNonModalTitle = document.title;
        }
        HistoryUtil.replaceState({}, document.title,
            HistoryUtil.formatSearchString(params.toString(), HistoryUtil.getDeepestTabId(document)));
    }

    static updateActiveTabs()
    {
        const modal = document.querySelector(".modal.show");
        const modalOnly = modal != null;
        const hash = modalOnly
            ? HistoryUtil.getDeepestTabId(modal)
            : HistoryUtil.getDeepestTabId(document);
        const dataTarget = "#" + (hash != null ? hash : modal.id);
        ElementUtil.setMainContent(dataTarget);
        ElementUtil.updateTitleAndDescription(new URLSearchParams(Session.locationSearch()), "#" + hash, dataTarget);
        const params = new URLSearchParams(Session.locationSearch());

        HistoryUtil.replaceState({}, document.title,
            HistoryUtil.formatSearchString(params.toString(), HistoryUtil.getDeepestTabId(document)));
    }

    static showAnchoredTabs()
    {
        if(Session.locationHash() == null || Session.locationHash().length == 0) return Promise.resolve();

        Util.setGeneratingStatus(STATUS.BEGIN);
        const promises = [];
        let prevTab = document.querySelector(Session.locationHash());
        HistoryUtil.showAnchoredTab(prevTab, promises);
        while(true)
        {
            const curTab = prevTab.parentNode.closest(".tab-pane");
            if(curTab == null || curTab == prevTab) break;
            HistoryUtil.showAnchoredTab(curTab, promises);
            prevTab = curTab;
        }
        return Promise.all(promises).then(e=>new Promise((res, rej)=>{Util.setGeneratingStatus(STATUS.SUCCESS); res();}));
    }

    static showAnchoredTab(tab, promises)
    {
        const tabEl = document.querySelector('.nav-pills a[data-target="#' + tab.id + '"]');
        if(tabEl.classList.contains("active")) return;

        if(tabEl.offsetParent != null) promises.push(new Promise((res, rej)=>ElementUtil.ELEMENT_RESOLVERS.set(tab.id, res)));
        $(tabEl).tab('show');
    }

    static restoreState(e)
    {
        Session.currentStateRestoration = Session.currentStateRestoration != null
            ? Session.currentStateRestoration.then(r=>HistoryUtil.doRestoreState(e))
            : HistoryUtil.doRestoreState(e);
    }

    static doRestoreState(e)
    {
        if(e != null && e.state == null) return;
        Util.setGeneratingStatus(STATUS.BEGIN);
        Session.isHistorical = true;
        const locationSearch = (e != null && e.state.locationSearch != null) ? e.state.locationSearch : window.location.search;
        const hash = (e != null && e.state.locationHash != null) ? e.state.locationHash : window.location.hash;
        Session.currentRestorationSearch =  locationSearch;
        Session.currentRestorationHash = hash;
        promises = [];
        lazyPromises = [];
        lazyPromises.push(e=>HistoryUtil.showAnchoredTabs());
        const params = new URLSearchParams(locationSearch);
        const isModal = params.get("m"); params.delete("m");
        const stringParams = params.toString();
        if(Session.currentSearchParams === stringParams) return Promise.all(promises)
            .then(e => {const ap = []; for(lp of lazyPromises) ap.push(lp()); return Promise.all(ap);})
            .then(e => new Promise((res, rej)=>{
                if(hash != null && hash.length > 0 && isModal != null)
                {
                    const lastModal = document.querySelector(hash).closest(".modal");
                    if(lastModal != null) BootstrapUtil.showModal(lastModal.id).then(e=>res());
                }
                else
                {
                    res();
                }
            }))
            .then(e => new Promise((res, rej)=>{
                HistoryUtil.updateActiveTabs();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }));

        const type = params.get("type"); params.delete("type");
        let scrollTo = null;
        switch(type)
        {
            case "ladder":
                const ratingAnchor = params.get("ratingAnchor"); params.delete("ratingAnchor");
                const idAnchor = params.get("idAnchor"); params.delete("idAnchor");
                const forward = params.get("forward"); params.delete("forward");
                const count = params.get("count"); params.delete("count");
                const formParams = params.toString();
                scrollTo = "generated-info-all";
                lazyPromises.push(e=>BootstrapUtil.hideCollapsible("form-ladder"));
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(LadderUtil.updateLadder(formParams, ratingAnchor, idAnchor, forward, count));
                promises.push(StatsUtil.updateQueueStats(formParams));
                promises.push(StatsUtil.updateLadderStats(formParams));
                promises.push(StatsUtil.updateLeagueBounds(formParams));
                break;
            case "character":
                const id = params.get("id"); params.delete("id");
                promises.push(CharacterUtil.showCharacterInfo(null, id));
                break;
            case "search":
                const name = params.get("name"); params.delete("name");
                scrollTo = "search-result-all";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(CharacterUtil.updateCharacterSearch(name));
                break;
            case "following-ladder":
                scrollTo = "following-ladder";
                lazyPromises.push(e=>BootstrapUtil.hideCollapsible("form-following-ladder"));
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(LadderUtil.updateMyLadder(params.toString()));
                break;
            case "modal":
                const modalId = params.get("id"); params.delete("id");
                promises.push(BootstrapUtil.hideActiveModal("error-generation"));
                lazyPromises.push(e=>BootstrapUtil.showModal(modalId));
                break;
            case null:
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal());
                break;
            default:
                break;
        }

        return Promise.all(promises)
        .then(e => {const ap = []; for(lp of lazyPromises) ap.push(lp()); return Promise.all(ap)})
        .then(e => new Promise((res, rej)=>{
            HistoryUtil.updateActiveTabs();
            if(document.querySelector("#main-tabs .nav-link.active").getAttribute("data-target") === "#about")
                ElementUtil.removeNofollowRels("about");
            Util.setGeneratingStatus(STATUS.SUCCESS);
            if(scrollTo != null) Util.scrollIntoViewById(scrollTo);
            res();
        }));
    }

}