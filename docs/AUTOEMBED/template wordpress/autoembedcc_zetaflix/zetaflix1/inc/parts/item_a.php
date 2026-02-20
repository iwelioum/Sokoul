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


$posttype = $post->post_type;
$postslug = $post->post_name;

// Compose data
//$quality = get_the_term_list($post->ID, 'ztquality');
$qualitys = get_the_terms($post->ID, 'ztquality');
$languages = get_the_terms($post->ID, 'ztlanguage');
$urating = zeta_isset($postmeta, '_starstruck_avg');
$imdbrat = zeta_isset($postmeta, 'imdbRating');
$release = zeta_isset($postmeta, 'release_date');
$airdate = zeta_isset($postmeta, 'first_air_date');
$viewsco = zeta_isset($postmeta, 'zt_views_count');
$runtime = zeta_isset($postmeta, 'runtime');
$maxwidth = zeta_get_option('max_width','1200');
$playicon = zeta_get_option('play_icon','play1');
$imdbrat = ($imdbrat) ? $imdbrat : '0';$posterStyle = zeta_get_option('poster_style','horizontal');$posterStyle = apply_filters('module_poster_style', $posterStyle);$posterSource = zeta_get_option('poster_source','meta');$posterSource = apply_filters('module_poster_style', $posterSource);$poster = ($posterStyle == 'Vertical') ? omegadb_get_poster('' , $post->ID) : omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'w300', $posterSource);

// End PHP
?>
<div id="item-<?php echo $post->ID;?>" class="display-item">
	<div class="item-box">
	      <a href="<?php the_permalink();?>" data-url="<?php echo $postslug;?>" data-ptype="<?php echo $posttype;?>" class="<?php echo $playicon;?>" title="<?php the_title(); ?>"></a>
	   <div class="item-desc-hover">
		  <?php zt_useritem_btn($post->ID, $posttype, $post->post_name);?>
	   </div>
	   <div class="item-data">
		  <h3><?php the_title(); ?></h3>
	   </div>
	   <img data-original="<?php echo $poster; ?>" class="thumb mli-thumb" alt="<?php the_title(); ?>" src="<?php echo $poster; ?>">
	   
   </div>
</div>