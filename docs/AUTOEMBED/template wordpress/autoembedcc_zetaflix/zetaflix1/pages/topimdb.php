<?php
/*
Template Name: ZT - TOP IMDb
*/
get_header();
$adarchive = zeta_compose_ad('_zetaflix_adarchive');
$adarchive2 = zeta_compose_ad('_zetaflix_adarchive2');
?>
<main>
<div class="display-page-heading"><h3><?php the_title(); ?> <span class="count"><?php echo zeta_get_option('itopimdb','50'); ?></span></h3> 

</div>
<div class="display-page result">
	<div class="page-body">
	<?php if($adarchive) echo '<div class="content-ads module-archive-ads">'.$adarchive.'</div>';?>
	<?php get_template_part('inc/parts/modules/top-imdb-page'); ?>
		<div class="clearfix"></div>
	</div>
	<?php if($adarchive2) echo '<div class="content-ads module-archive-ads">'.$adarchive2.'</div>';?>
</div>
</main>
<?php get_template_part('inc/parts/sidebar'); ?>
<div class="clearfix"></div>
<?php get_footer(); ?>
