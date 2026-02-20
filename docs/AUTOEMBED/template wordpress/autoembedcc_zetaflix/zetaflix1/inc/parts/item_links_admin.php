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
$usid = get_current_user_id();
$psid = $post->ID;
$stus = get_post_status();
$atho = get_the_author_meta('nickname');
$gtpl = get_the_permalink($psid);
$prid = wp_get_post_parent_id($psid);
$ptit = get_the_title($prid);
$pprl = get_the_permalink($prid);
$murl = get_post_meta($psid,'_zetal_url',true);
$type = get_post_meta($psid,'_zetal_type',true);
$viws = get_post_meta($psid,'zt_views_count',true);
$date = human_time_diff(get_the_time('U',$psid), current_time('timestamp',$psid));
$viws = ($viws) ? $viws : '0';
$fico = ($type == __z('Torrent')) ? 'utorrent.com' : zeta_compose_domainname($murl);
$domn = ($type == __z('Torrent')) ? 'Torrent' : zeta_compose_domainname($murl);
$fico = '<img src="'.ZETA_GICO.$fico.'" />';

// Compose View
$out  = "<tr id='adm-{$psid}'>";
$out .= "<td><div class='link-opt'>{$fico}<a href='{$gtpl}' target='_blank'>{$domn}</a></div></td>";
$out .= "<td><a href='{$pprl}' target='_blank'>{$ptit}</a></td>";
$out .= "<td><span>{$atho}</span></td>";
$out .= "<td class='views'><span>{$viws}</span></td>";
$out .= "<td class='status'><span>{$stus}</span></td>";
$out .= "<td class='control'><ul class='manage-links'>";
if(current_user_can('administrator')){
    if($stus == 'publish'){
        $out .= "<li><a href='#' class='control_admin_link updt' data-user='{$usid}' data-id='{$psid}' data-status='pending'>".__z('Disable')."</a></li>";
    } else {
        $out .= "<li><a href='#' class='control_admin_link updt' data-user='{$usid}' data-id='{$psid}' data-status='publish'>".__z('Enable')."</a></li>";
    }
	$visibility = ($stus == 'trash') ? "style='display:none'" : "";
    $out .= "<li {$visibility}><a href='#' class='control_admin_link dlt' data-user='{$usid}' data-id='{$psid}' data-status='trash'>".__z('Delete')."</a></li>";
}
$out .= "</ul></td></tr>";

// The view
echo $out;
