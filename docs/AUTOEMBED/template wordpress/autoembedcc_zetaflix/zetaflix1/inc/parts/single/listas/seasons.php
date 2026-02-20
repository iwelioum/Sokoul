<?php
/**
 * @author Zetathemes
 * @since 1.0.0
 */

// Main Data
$itmdb = get_post_meta($post->ID,'ids',true);
$seaso = get_post_meta($post->ID,'temporada',true);
$epis  = get_post_meta($post->ID,'episodio',true);
$nonce = wp_create_nonce('zt-tv-episode');
$episode = get_query_var('episode');
$episode = (isset($episode)) ? explode('/', $episode) : null;
$ep = (isset($episode[0])) ? (int)$episode[0] : null;
$watchv = (isset($episode[3])) ? $episode[3] : null;
$watch = (isset($episode[4])) ? (int)$episode[4] : null;
/*=====================================================*/
$query = OmegadbHelpers::GetAllEpisodes($itmdb,$seaso);
/*=====================================================*/
// Start Query


	$html_out = "<div class='content-episodes full'>
				 <div class='episodes-head'>
				 <div class='seasons-switch left'>
				 <button class='seasons-select active' disabled='disabled'>Season <span class='ss-num'> {$seaso}</span></button>";
	$html_out  .= "</div>
				<div class='ldng'></div>
				  </div> ";
	$html_out .= "<div class='season-select'>";
	$html_out .= "<ul id='season-listep-{$seaso}' class='episodes-list'>";
	if($query && is_array($query)){
		foreach($query as $post_id){
			// Post Data
			$senumb = get_post_meta($post_id,'temporada', true);
			$image = omegadb_get_poster('', $post_id,'zt_episode_a','zt_backdrop','w154');
			$episo = get_post_meta($post_id,'episodio', true);
			$edate = get_post_meta($post_id,'air_date', true);
			$edate = zeta_date_compose($edate, false);
			$name  = get_post_meta($post_id,'episode_name', true);
			$plink = get_permalink($post_id);
			$title = !empty($name) ? $name : __('Episode').' '.$episo;
			$active = ($ep == $episo) ? 'active' : null;
			$active = ($post->post_type === 'episodes' && $epis === $episo) ? 'active' : $active;
			$url = (isset($args['ajaxep'])) ? "class='play-ep' data-sec='".$nonce."' data-type='ssep' data-pid='{$post_id}' data-epid='{$episo}' data-ssid='{$senumb}'" : "href='{$plink}'";
			// The View
			
			$html_out .= "<li class='ep-{$episo} {$active}'>
							<a {$url}>
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
		$html_out .= "<li class='noep'><span class='noep-msg'>".__z('There are still no episodes this season')."</span></li>";
	}
	$html_out .= "</div></div>";
// Compose viewer HTML
echo apply_filters('zetaflix_list_seasons', $html_out, $itmdb);
