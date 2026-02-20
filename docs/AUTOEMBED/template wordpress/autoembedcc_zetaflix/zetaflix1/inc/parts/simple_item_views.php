<article class="simple <?php echo get_post_type(); ?>" id="v<?php the_id(); ?>">
	<div class="poster">
		<img src="<?php echo omegadb_get_poster($post->ID); ?>" alt="<?php the_title(); ?>">
		<div class="profile_control">
			<span><a href="<?php the_permalink(); ?>"><?php _z('View'); ?></a></span>
			<span><a class="user_views_control buttom-control-v-<?php the_id(); ?>" data-nonce="<?php echo wp_create_nonce('zt-view-noce'); ?>" data-postid="<?php the_id(); ?>"><?php _z('Remove'); ?></a></span>
		</div>
	</div>
	<div class="data">
		<h3><a href="<?php the_permalink() ?>"><?php the_title(); ?></a></h3>
		<?php if($mostrar = $terms = strip_tags( $terms = get_the_term_list( $post->ID, 'ztyear'))) {  ?>
		<span><?php echo $mostrar; ?></span>
		<?php } else { ?>
		<span>&nbsp;</span>
		<?php } ?>
	</div>
</article>
