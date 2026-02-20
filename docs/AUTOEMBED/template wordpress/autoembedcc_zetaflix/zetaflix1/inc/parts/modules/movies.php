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
$sldr = zeta_is_true('moviesmodcontrol','slider');
$auto = zeta_is_true('moviesmodcontrol','autopl');
$orde = zeta_get_option('moviesmodorderby','date');
$ordr = zeta_get_option('moviesmodorder','DESC');
$dspl = zeta_get_option('moviesmdisplay','slider');
$dspl = apply_filters('module_poster_display_mv', $dspl);
$pitm = ($dspl == 'grid') ? zeta_get_option('moviesgitems','18') : zeta_get_option('moviesitems','18');
$titl = zeta_get_option('moviestitle','movies');
$pmlk = get_post_type_archive_link('movies');
$totl = zeta_total_count('movies');
$eowl = ($sldr == true) ? 'id="zt-movies" ' : false;

// Compose Query
$query = array(
	'post_type' => array('movies'),
	'showposts' => $pitm,
	'orderby' 	=> $orde,
	'order' 	=> $ordr
);

// End Data
?>

	<div class="home-module <?php echo $dspl;?> stream movies" data-module-id="featured-movies">	<div class="module-title <?php echo (!$titl) ? 'no-title' : null ;?>">
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






