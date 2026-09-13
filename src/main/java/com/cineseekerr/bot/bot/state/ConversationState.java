package com.cineseekerr.bot.bot.state;

import com.cineseekerr.bot.model.*;
import java.util.List;

/** Mutable per-chat state; release GUIDs stay server-side in this state, never callbacks. */
public final class ConversationState {
    private ConversationStep step = ConversationStep.AWAITING_MOVIE_CHOICE;
    private List<TmdbTitle> candidates = List.of(); private TmdbTitle title;
    private List<TmdbSeason> seasons = List.of(); private Integer season; private Integer episode;
    private Integer arrId;
    private List<SearchResult> allResults = List.of(), filtered = List.of(), shortlist = List.of();
    private Resolution qualityFilter; private Language audioFilter, subtitleFilter; private VideoCodec formatFilter;
    private Integer messageId; private Runnable backAction;
    public ConversationStep step(){return step;} public void setStep(ConversationStep x){step=x;}
    public List<TmdbTitle> candidates(){return candidates;} public void setCandidates(List<TmdbTitle>x){candidates=List.copyOf(x);}
    public TmdbTitle title(){return title;} public void setTitle(TmdbTitle x){title=x;}
    public List<TmdbSeason> seasons(){return seasons;} public void setSeasons(List<TmdbSeason>x){seasons=List.copyOf(x);}
    public Integer season(){return season;} public void setSeason(Integer x){season=x;} public Integer episode(){return episode;} public void setEpisode(Integer x){episode=x;}
    public Integer arrId(){return arrId;} public void setArrId(Integer x){arrId=x;}
    public List<SearchResult> allResults(){return allResults;} public void setAllResults(List<SearchResult>x){allResults=List.copyOf(x);}
    public List<SearchResult> filtered(){return filtered;} public void setFiltered(List<SearchResult>x){filtered=List.copyOf(x);}
    public List<SearchResult> shortlist(){return shortlist;} public void setShortlist(List<SearchResult>x){shortlist=List.copyOf(x);}
    public Resolution qualityFilter(){return qualityFilter;} public void setQualityFilter(Resolution x){qualityFilter=x;}
    public Language audioFilter(){return audioFilter;} public void setAudioFilter(Language x){audioFilter=x;} public Language subtitleFilter(){return subtitleFilter;} public void setSubtitleFilter(Language x){subtitleFilter=x;}
    public VideoCodec formatFilter(){return formatFilter;} public void setFormatFilter(VideoCodec x){formatFilter=x;}
    public Integer messageId(){return messageId;} public void setMessageId(Integer x){messageId=x;} public Runnable backAction(){return backAction;} public void setBackAction(Runnable x){backAction=x;}
}
