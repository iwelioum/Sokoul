<form class="search-form" action="<?php echo esc_url( home_url('/') ); ?>">
    <input class="search-input" type="text" name="s" placeholder="<?php _z('Search...'); ?>" value="<?php echo get_search_query(); ?>" spellcheck="false">
    <button type="submit" id=""><span class="fas fa-search"></span></button>
</form>
