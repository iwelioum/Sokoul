<?php
/**
 * @author Zetathemes
 * @since 1.0.0
 */

// Main data
$tmdb = get_post_meta($post->ID,'ids',true);
$ctrl = get_post_meta($post->ID,'clgnrt',true);
/*=====================================================*/
$query_seasons = OmegadbHelpers::GetAllSeasons($tmdb);
/*=====================================================*/
// Start Query


$ajaxep = zeta_get_option('playajaxep');
$style = zeta_get_option('epliststyle');
$style_class = ($style !== 'simp') ? 'full' : null;

$season_var = get_query_var('season');
$season_var = explode('/',$season_var);
$ss_val = (isset($season_var[0])) ? (int)$season_var[0] : null;
$ep_var = (isset($season_var[1])) ? $season_var[1] : null;
$ep_val = (isset($season_var[2])) ? (int)$season_var[2] : null;
$watch_var = (isset($season_var[3])) ? $season_var[3] : null;
$watch_val = (isset($season_var[4])) ? (int)$season_var[4] : null;
$class_ajax = '';
if($ajaxep){
	$nonce = wp_create_nonce('zt-tv-episode');
	$class_ajax = "class='play-ep'";
}
	
if($query_seasons && is_array($query_seasons) && $ctrl == true){
	$season0 = $query_seasons[0];
	$season1 = get_post_meta($season0, 'temporada', true);
	$season1 = ($ss_val) ? $ss_val : $season1;
	$html_out = "<div class='content-episodes {$style_class}'>
				 <div class='episodes-head'>
				 <div class='seasons-switch left'>
				 <button class='seasons-select'>Season <span class='ss-num'>{$season1}</span></button>
				 <ul class='seasons-list'>";
	$senumb = 0;
	$totalep = 0;
	$episodes[$senumb] = array();
	foreach($query_seasons as $season){
		$senumb = get_post_meta($season,'temporada', true);
		$episodes[$senumb] = OmegadbHelpers::GetAllEpisodes($tmdb,$senumb);		
		$totalep = count($episodes[$senumb]);
		$html_out .= "<li class='ss-{$senumb}'><a data-snum='{$senumb}'>".__z("Season")." {$senumb}<span class='ep-count'>({$totalep} Episodes)</span></a></li>";
	}
	$html_out  .= "</ul>
					</div>
					<div class='ldng'></div>
				  </div> ";
	$html_out .= "<div class='season-select'>";
	$numb = 0;
    foreach($query_seasons as $season){
        $senumb = get_post_meta($season,'temporada', true);
        $aidate = get_post_meta($season,'air_date', true);
        $rating = get_post_meta($season,'_starstruck_avg', true);
        /*=====================================================*/
        //$query_episodes = DOmgdbHelpers::GetAllEpisodes($tmdb,$senumb);
        /*=====================================================*/
		
		$class = ($senumb == $ss_val) ? 'active' : null;
	$html_out .= "<ul id='season-listep-{$senumb}' class='episodes-list {$class}'>";
	if($episodes[$senumb] && is_array($episodes[$senumb])){
		foreach($episodes[$senumb] as $episode){
			// Post Data
			$image = omegadb_get_poster('', $episode,'zt_episode_a','zt_backdrop','w154');
			$name  = get_post_meta($episode,'episode_name', true);
			$episo = get_post_meta($episode,'episodio', true);
			$edate = get_post_meta($episode,'air_date', true);
			$edate = zeta_date_compose($edate, false);
			$plink = get_permalink($episode);
			$title = !empty($name) ? $name : __('Episode').' '.$episo;
			$url = ($ajaxep) ? "data-type='tvep' data-sec='".$nonce."' data-pid='{$episode}' data-epid='{$episo}' data-ssid='{$senumb}'" : "href='{$plink}'";
					$class = ($ss_val == $senumb && $episo == $ep_val) ? 'active' : null;
					$class_ep = ($ajaxep) ? 'ep-'.$episo : null;
		$html_out .= "<li class='{$class_ep} {$class}'>
						<a {$class_ajax} {$url}>
						  <span class='ep-num'>{$episo}</span> 
						  <span class='ep-thumb'><img src='{$image}'></span>
						  <span class='data'>
							<span class='ep-title'>{$title}</span>
							<span class='ep-date'>{$edate}</span>
						  </span>
						</a>
					  </li>";
		}
	}else{
		$html_out .= "<li class='noep'><span class='noep-msg'>".__z("No episodes available on selected season")."</span></li>";
	}
	$html_out .= "</ul>";
	        $numb++;
	}
	$html_out .= "</div>";
	$html_out .= "</div>";
    
    echo apply_filters('zetaflix_list_seasons_episodes', $html_out, $tmdb);
}
