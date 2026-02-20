<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/
get_header();
$adarchive = zeta_compose_ad('_zetaflix_adarchive');
$adarchive2 = zeta_compose_ad('_zetaflix_adarchive2');
$sidebar = zeta_get_option('sidebar_position_archives','right');


echo '<main>';
//HEADING
echo '<div class="display-page-heading">';
echo '<h3>'.__z('Episodes').'</h3>';
if(!have_posts()){
	echo '<p class="no-result">'.__z('No Content Available').'</p>';
}
echo '</div>';
if(have_posts()){
//BODY
	echo '<div class="display-page result">';
	if($adarchive) echo '<div class="content-ads module-archive-ads">'.$adarchive.'</div>';
	echo '<div class="page-body episodes">';
	while(have_posts()){
        the_post();
		get_template_part('inc/parts/item_epis');
	}	
	echo '<div class="clearfix"></div>';
	echo '</div>';
	if($adarchive2) echo '<div class="content-ads module-archive-ads">'.$adarchive2.'</div>';
	echo '<div class="page-nav">';
	zeta_pagination();
	echo '</div>';
	echo '</div>';
}

echo '</main>';

get_template_part('inc/parts/sidebar', null, array('archv' => 'episodes'));

get_footer();
