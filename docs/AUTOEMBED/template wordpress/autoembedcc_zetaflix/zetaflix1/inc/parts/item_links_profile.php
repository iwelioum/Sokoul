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

// Link all data
$psid = $post->ID;
$gtpl = get_the_permalink($psid);
$prid = wp_get_post_parent_id($psid);
$ptit = get_the_title($prid);
$pprl = get_the_permalink($prid);
$murl = get_post_meta($psid, '_zetal_url', true);
$type = get_post_meta($psid, '_zetal_type', true);
$lang = get_post_meta($psid, '_zetal_lang', true);
$qual = get_post_meta($psid, '_zetal_quality', true);
$viws = get_post_meta($psid, 'zt_views_count', true);
$date = human_time_diff(get_the_time('U',$psid), current_time('timestamp',$psid));
$viws = ($viws) ? $viws : '0';
$fico = ($type == __z('Torrent')) ? 'utorrent.com' : zeta_compose_domainname($murl);
$domn = ($type == __z('Torrent')) ? 'Torrent' : zeta_compose_domainname($murl);
$fico = '<img src="'.ZETA_GICO.$fico.'" />';

// Compose View
$out = "<tr link-id='{$psid}'>";
$out .= "<td><div class='link-opt'>{$fico}<a href='{$gtpl}' target='_blank'>{$domn}</a></div></td>";
$out .= "<td><a href='{$pprl}' target='_blank'>{$ptit}</a></td>";
$out .= "<td><span>{$qual}</span></td>";
$out .= "<td><span>{$lang}</span></td>";
$out .= "<td><span>{$viws}</span></td>";
$out .= "<td><span>{$date}</span></td>";
$out .= "</tr>";

// The view
echo $out;
