
<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
// Main Query
$episode_pagi = OmegadbHelpers::EpisodeNav($tmdbids,$temporad,$episode);
// Compose data
$prev_episode = zeta_isset($episode_pagi,'prev');
$next_episode = zeta_isset($episode_pagi,'next');
$tvshow_posts = zeta_isset($episode_pagi,'tvsh');
// Compose Links
$link_prev = !empty($prev_episode) ? 'href="'.$prev_episode['permalink'].'" title="'.$prev_episode['title'].'"' : 'href="#" class="disabled"';
$link_next = !empty($next_episode) ? 'href="'.$next_episode['permalink'].'" title="'.$next_episode['title'].'"' : 'href="#" class="disabled"';
$link_tvsh = !empty($tvshow_posts) ? 'href="'.$tvshow_posts['permalink'].'" title="'.$tvshow_posts['title'].'"' : 'href="#" class="disabled"';
// View HTML
$out_html = "<div class='episode-navigation'>";
$out_html .= "<div class='nav'>";
$out_html .= "<a {$link_prev} class='nav-prev'><i class='fa-solid fa-backward-step'></i> <span>".__z('PREV')."</span></a>";
$out_html .= "</div><div class='nav'>";
$out_html .= "<a {$link_tvsh} class='nav-all'><i class='fa-solid fa-list'></i> <span>".__z('ALL')."</span></a>";
$out_html .= "</div><div class='nav'>";
$out_html .= "<a {$link_next} class='nav-next'><span>".__z('NEXT')."</span> <i class='fa-solid fa-forward-step'></i></a>";
$out_html .= "</div></div>";
// Echo And Filter Navigator
echo apply_filters('zeta_episode_navigator', $out_html, $tmdbids.$temporad.$episode);
