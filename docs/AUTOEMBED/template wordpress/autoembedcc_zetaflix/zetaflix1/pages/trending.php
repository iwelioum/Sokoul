<?php
/*
Template Name: ZT - Trending page
*/


get_header();
$dt = isset( $_GET['get'] ) ? $_GET['get'] : null;
$admin = isset( $_GET['admin'] ) ? $_GET['admin'] : null;
if($dt == 'mv'):
	$setion = array('movies');
elseif($dt == 'tv'):
	$setion = array('tvshows');
else:
	$setion = array('movies','tvshows');
endif;


$maxwidth = zeta_get_option('max_width','1200');
$maxwidth = ($maxwidth >= 1400) ? 'full' : 'normal';
$adarchive = zeta_compose_ad('_zetaflix_adarchive');
$adarchive2 = zeta_compose_ad('_zetaflix_adarchive2');
?>


<main>
<div class="display-page-heading"><h3><?php the_title(); ?> </span></h3> 
<ul class="heading-submenu">
<li <?php echo $dt == '' ? 'class="active"' : ''; ?>><a href="<?php the_permalink() ?>">All</a></li>
<li <?php echo $dt == 'mv' ? 'class="active"' : ''; ?>><a href="<?php the_permalink() ?>?get=mv">Movies</a></li>
<li <?php echo $dt == 'tv' ? 'class="active"' : ''; ?>><a href="<?php the_permalink() ?>?get=tv">TV Shows</a></li>
</ul>

</div>
<div class="display-page result">
	<div class="page-body">
	<?php if($adarchive) echo '<div class="content-ads module-archive-ads">'.$adarchive.'</div>'; ?>
<?php
		$paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
query_posts(array(
	'post_type'    => $setion,
	'post_status'  => 'publish',
	'meta_key'     => 'zt_views_count',
	'orderby'      => 'meta_value_num',
	'order'        => 'DESC',
	'paged'        => $paged
));

		while (have_posts()):
			the_post(); ?>
		<?php get_template_part('inc/parts/item_arch'); ?>
		<?php endwhile; ?>
		<div class="clearfix"></div>
	</div>
	<?php if($adarchive2) echo '<div class="content-ads module-archive-ads">'.$adarchive2.'</div>';?>
	<?php zeta_pagination(); ?>
</div>
<div class="clearfix"></div>
</main>
<?php get_template_part('inc/parts/sidebar'); ?>
<div class="clearfix"></div>
<?php get_footer(); ?>

