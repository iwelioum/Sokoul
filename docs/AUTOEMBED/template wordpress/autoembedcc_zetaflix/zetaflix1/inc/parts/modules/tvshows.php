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

// Compose data MODULE
$sldr = zeta_is_true('tvmodcontrol','slider');
$auto = zeta_is_true('tvmodcontrol','autopl');
$dspl = zeta_get_option('tvshowsmdisplay','slider');
$dspl = apply_filters('module_poster_display_tv', $dspl);
$orde = zeta_get_option('tvmodorderby','date');
$ordr = zeta_get_option('tvmodorder','DESC');
$pitm = ($dspl == 'grid') ? zeta_get_option('tvshowsgitems','18') : zeta_get_option('tvshowsitems','14');
$titl = zeta_get_option('tvtitle','TV Shows');
$pmlk = get_post_type_archive_link('tvshows');
$totl = zeta_total_count('tvshows');
$eowl = ($sldr == true) ? 'id="zt-tvshows" ' : false;

// Compose Query
$query = array(
	'post_type' => array('tvshows'),
	'showposts' => $pitm,
	'orderby' 	=> $orde,
	'order' 	=> $ordr
);

// End Data
?>

	<div class="home-module <?php echo $dspl;?> stream tvshows" data-module-id="featured-tvshows">
	<div class="module-title <?php echo (!$titl) ? 'no-title' : null ;?>">
		<div class="wrapper-box">
			<span><?php echo $titl; ?></span>
			<a href="<?php echo $pmlk; ?>"><?php _z('View All');?></a>
		</div>
	</div>
	<div class="module-content <?php echo ($dspl == 'slider') ? 'owl-carousel' : null;?>">
	<?php query_posts($query); while(have_posts()){ the_post(); 
	
	if($dspl == 'grid'){
		get_template_part('inc/parts/item_arch'); 
	}else{
		get_template_part('inc/parts/item'); 
	}
	
	} wp_reset_query(); ?>
	<?php echo ($dspl != 'slider') ? '<div class="clearfix"></div>' : null;?>
	</div>
</div>




