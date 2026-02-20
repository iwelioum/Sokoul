
<div id="archive-content" class="animation-2 items">
<?php get_template_part('inc/parts/modules/letter'); ?>
<?php if (have_posts()) : while (have_posts()) : the_post(); get_template_part('inc/parts/item'); endwhile; else: ?>
<div class="no-result animation-2">
	<h2><?php _z('No results to show with'); ?> <span><?php echo wp_strip_all_tags($_GET['s']); ?></span></h2>
	<strong><?php _z('Suggestions'); ?>:</strong>
	<ul>
		<li><?php _z('Make sure all words are spelled correctly.'); ?></li>
		<li><?php _z('Try different keywords.'); ?></li>
		<li><?php _z('Try more general keywords.'); ?></li>
	</ul>
</div>
<?php endif; ?>
</div>